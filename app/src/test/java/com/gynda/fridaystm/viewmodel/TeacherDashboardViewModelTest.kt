package com.gynda.fridaystm.viewmodel

import com.gynda.fridaystm.data.model.IzinRecord
import com.gynda.fridaystm.data.model.LarkamRecord
import com.gynda.fridaystm.data.model.PresensiRecord
import com.gynda.fridaystm.data.model.AttendanceDay
import com.gynda.fridaystm.data.model.AttendanceDayProjector
import com.gynda.fridaystm.data.model.User
import com.gynda.fridaystm.data.repository.TeacherDashboardRepository
import com.gynda.fridaystm.viewmodel.FakeTimeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

/**
 * Unit tests for [TeacherDashboardViewModel] on JVM fakes — no Firestore, no Android.
 *
 * Two spec cases:
 *  - loadClassData_success_calculatesAggregateStatsCorrectly
 *  - filterByClass_updatesRealtimeStreamAndList
 *
 * Plus a sanity check for default date/class.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TeacherDashboardViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val fixedDate = "2026-09-06"
    private val fixedDateTime = LocalDateTime.of(2026, 9, 6, 8, 0)

    // Helpers to build fake data
    private fun makeUser(uid: String, nama: String, kelas: String) = User(
        uid = uid, nis = "NIS$uid", nama = nama, grade = 11, kelas = kelas, role = "student",
    )
    private fun makePresensi(userId: String, kelas: String, time: String = "06:45:00") = PresensiRecord(
        userId = userId, timestamp = "${fixedDate}T$time", studentClass = kelas,
        studentName = "Nama $userId", imageUrl = "https://img/$userId.jpg", lat = -6.88, lng = 107.53,
    )
    private fun makeIzin(userId: String, kelas: String) = IzinRecord(
        userId = userId, nama = "Nama $userId", kelas = kelas,
        tipe = "SAKIT", alasan = "Sakit", startDate = fixedDate, endDate = fixedDate,
        proofUrl = "", status = "APPROVED",
    )
    private fun makeLarkam(userId: String, km: Float) = LarkamRecord(
        userId = userId, distanceKm = km, timestamp = "${fixedDate}T07:00:00",
    )

    private class FakeTeacherRepo(
        private val usersByClass: Map<String, List<User>> = emptyMap(),
        private val presensiByClassDate: Map<Pair<String, String>, List<PresensiRecord>> = emptyMap(),
        private val izinByClassDate: Map<Pair<String, String>, List<IzinRecord>> = emptyMap(),
        private val larkamByClassDate: Map<Pair<String, String>, List<LarkamRecord>> = emptyMap(),
    ) : TeacherDashboardRepository {
        // track requested filters for the second test
        val requestedClasses = mutableListOf<String>()
        val requestedDates = mutableListOf<String>()

        override fun observeUsersByClass(kelas: String): Flow<List<User>> {
            requestedClasses.add(kelas)
            return flowOf(usersByClass[kelas].orEmpty())
        }
        override fun observePresensi(kelas: String, date: String): Flow<List<AttendanceDay>> {
            requestedDates.add(date)
            return kotlinx.coroutines.flow.flow { emit(AttendanceDayProjector.merge(emptyList(), presensiByClassDate[kelas to date].orEmpty())) }
        }
        override fun observeIzin(kelas: String, date: String): Flow<List<IzinRecord>> =
            flowOf(izinByClassDate[kelas to date].orEmpty())
        override fun observeLarkam(kelas: String, date: String): Flow<List<LarkamRecord>> =
            flowOf(larkamByClassDate[kelas to date].orEmpty())
        override suspend fun fetchAvailableClasses(): Result<List<String>> =
            Result.success(usersByClass.keys.sorted())
    }

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun viewModel(repo: FakeTeacherRepo) = TeacherDashboardViewModel(
        repository = repo,
        timeProvider = FakeTimeProvider(fixedDateTime),
        authRepository = FakeAuthRepository(initialUid = "teacher"),
    )

    @Test
    fun rosterChangeClearsSummaryAndWaitsForFreshEvidence() = runTest(dispatcher) {
        val kelas = "XI RPL A"
        val roster = MutableStateFlow(listOf(makeUser("old", "Old", kelas)))
        val evidence = kotlinx.coroutines.flow.MutableSharedFlow<List<AttendanceDay>>()
        val base = FakeTeacherRepo()
        val repo = object : TeacherDashboardRepository by base {
            override fun observeUsersByClass(kelas: String) = roster
            override fun observePresensi(kelas: String, date: String) = evidence
        }
        val vm = TeacherDashboardViewModel(repo, FakeTimeProvider(fixedDateTime), FakeAuthRepository(initialUid = "teacher"))
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        assertTrue(vm.uiState.value.isLoading)
        evidence.emit(AttendanceDayProjector.merge(emptyList(), listOf(makePresensi("old", kelas))))
        runCurrent()
        assertEquals(1, vm.uiState.value.stats.totalHadir)
        roster.value = listOf(makeUser("new", "New", kelas))
        runCurrent()
        assertTrue("New roster must not use previous evidence as completed data", vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.students.isEmpty())
        evidence.emit(AttendanceDayProjector.merge(emptyList(), listOf(makePresensi("new", kelas))))
        runCurrent()
        assertTrue(!vm.uiState.value.isLoading)
        assertEquals("new", vm.uiState.value.students.single().user.uid)
        assertEquals(1, vm.uiState.value.stats.totalHadir)
    }

    @Test
    fun errorCanRecoverOnRefreshOrAccountChange() = runTest(dispatcher) {
        val base = FakeTeacherRepo()
        var fail = true
        val repo = object : TeacherDashboardRepository by base {
            override fun observePresensi(kelas: String, date: String): Flow<List<AttendanceDay>> =
                kotlinx.coroutines.flow.flow {
                    if (fail) error("offline")
                    emit(emptyList())
                }
        }
        val auth = FakeAuthRepository(initialUid = "teacher")
        val vm = TeacherDashboardViewModel(repo, FakeTimeProvider(fixedDateTime), auth)
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        assertEquals("offline", vm.uiState.value.error)
        fail = false
        vm.refresh()
        runCurrent()
        assertEquals(null, vm.uiState.value.error)
        auth.signOut()
        runCurrent()
        assertTrue(vm.uiState.value.students.isEmpty())
        assertTrue(vm.uiState.value.error != null)
    }

    @Test
    fun malformedLegacyIsAnErrorNotSuccessfulAbsence() = runTest(dispatcher) {
        val kelas = "XI RPL A"
        val vm = viewModel(FakeTeacherRepo(
            usersByClass = mapOf(kelas to listOf(makeUser("u1", "Siswa", kelas))),
            presensiByClassDate = mapOf((kelas to fixedDate) to listOf(makePresensi("u1", kelas, "invalid"))),
        ))
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        assertTrue(vm.uiState.value.error != null)
        assertTrue(vm.uiState.value.students.isEmpty())
    }

    @Test
    fun refreshActuallyResubscribesToSources() = runTest(dispatcher) {
        val repo = FakeTeacherRepo()
        val vm = viewModel(repo)
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        val before = repo.requestedClasses.size
        vm.refresh()
        runCurrent()
        assertTrue("Refresh must re-open listeners, not just delay", repo.requestedClasses.size > before)
    }

    @Test
    fun loadClassData_success_calculatesAggregateStatsCorrectly() = runTest(dispatcher) {
        // XI RPL A: 20 students
        val kelas = "XI RPL A"
        val users = (1..20).map { makeUser("u$it", "Siswa $it", kelas) }
        // 12 hadir (u1..u12), 3 izin (u13..u15), rest 5 belum (u16..u20)
        val presensi = (1..12).map { makePresensi("u$it", kelas) }
        val izin = (13..15).map { makeIzin("u$it", kelas) }
        val larkam = listOf(makeLarkam("u1", 5.5f), makeLarkam("u2", 7.0f), makeLarkam("u3", 10.2f)) // total 22.7
        val repo = FakeTeacherRepo(
            usersByClass = mapOf(kelas to users),
            presensiByClassDate = mapOf((kelas to fixedDate) to presensi),
            izinByClassDate = mapOf((kelas to fixedDate) to izin),
            larkamByClassDate = mapOf((kelas to fixedDate) to larkam),
        )
        val vm = viewModel(repo)
        val job = launch { vm.uiState.collect {} }
        runCurrent()

        val state = vm.uiState.value
        // isLoading should be false after first emission
        assertTrue(!state.isLoading)
        assertEquals(kelas, state.selectedClass)
        assertEquals(fixedDate, state.selectedDate)
        assertEquals(12, state.stats.totalHadir)
        assertEquals(3, state.stats.totalIzin)
        assertEquals(5, state.stats.totalBelum)
        // 5.5+7.0+10.2 = 22.7
        assertEquals(22.7, state.stats.totalLarkamKm, 0.01)
        assertEquals(20, state.students.size)
        // verify student status mapping
        val hadirCount = state.students.count { it.status.name == "LEGACY" }
        val izinCount = state.students.count { it.status.name == "IZIN" }
        val belumCount = state.students.count { it.status.name == "BELUM" }
        assertEquals(12, hadirCount)
        assertEquals(3, izinCount)
        assertEquals(5, belumCount)
        // check that hadir item has foto & jam
        val firstHadir = state.students.first { it.status.name == "LEGACY" }
        assertTrue(firstHadir.presensi?.imageUrl?.isNotBlank() == true)
        assertTrue(firstHadir.presensi?.timestamp?.contains("06:") == true)

        job.cancel()
    }

    @Test
    fun duplicatePresensiUsesEarliestValidTime() = runTest(dispatcher) {
        val kelas = "XI RPL A"
        val vm = viewModel(FakeTeacherRepo(
            usersByClass = mapOf(kelas to listOf(makeUser("u1", "Siswa 1", kelas))),
            presensiByClassDate = mapOf((kelas to fixedDate) to listOf(
                makePresensi("u1", kelas, "06:45:00"),
                makePresensi("u1", kelas, "07:15:00"),
            )),
        ))
        val job = backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        assertEquals(1, vm.uiState.value.stats.totalHadir)
        assertEquals("${fixedDate}T06:45:00", vm.uiState.value.students.single().presensi?.timestamp)
        job.cancel()
    }

    @Test
    fun pendingAndRejectedPermitsAreNotExcused() = runTest(dispatcher) {
        val kelas = "XI RPL A"
        val users = (1..3).map { makeUser("u$it", "Siswa $it", kelas) }
        val permits = listOf(
            makeIzin("u1", kelas),
            makeIzin("u2", kelas).copy(status = "PENDING"),
            makeIzin("u3", kelas).copy(status = "REJECTED"),
        )
        val vm = viewModel(FakeTeacherRepo(
            usersByClass = mapOf(kelas to users),
            izinByClassDate = mapOf((kelas to fixedDate) to permits),
        ))
        val job = backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        assertEquals(1, vm.uiState.value.stats.totalIzin)
        assertEquals(2, vm.uiState.value.stats.totalBelum)
        assertEquals(listOf("IZIN", "BELUM", "BELUM"), vm.uiState.value.students.map { it.status.name })
        job.cancel()
    }

    @Test
    fun filterByClass_updatesRealtimeStreamAndList() = runTest(dispatcher) {
        val kelasA = "XI RPL A"
        val kelasB = "XI RPL B"
        val usersA = (1..10).map { makeUser("a$it", "A $it", kelasA) }
        val usersB = (1..5).map { makeUser("b$it", "B $it", kelasB) }
        val presensiA = (1..6).map { makePresensi("a$it", kelasA) }
        val presensiB = (1..2).map { makePresensi("b$it", kelasB) }
        val repo = FakeTeacherRepo(
            usersByClass = mapOf(kelasA to usersA, kelasB to usersB),
            presensiByClassDate = mapOf(
                (kelasA to fixedDate) to presensiA,
                (kelasB to fixedDate) to presensiB,
            ),
            izinByClassDate = emptyMap(),
            larkamByClassDate = emptyMap(),
        )
        val vm = viewModel(repo)
        val job = launch { vm.uiState.collect {} }
        runCurrent()

        // Initially default class is XI RPL A
        assertEquals(kelasA, vm.uiState.value.selectedClass)
        assertEquals(10, vm.uiState.value.students.size)
        assertEquals(6, vm.uiState.value.stats.totalHadir)

        // Switch to B
        vm.onClassSelected(kelasB)
        runCurrent()

        val after = vm.uiState.value
        assertEquals(kelasB, after.selectedClass)
        assertEquals(5, after.students.size)
        assertEquals(2, after.stats.totalHadir)
        // Verify repository was queried for new class
        assertTrue(repo.requestedClasses.contains(kelasB))

        job.cancel()
    }

    @Test
    fun filterByDate_updatesStream() = runTest(dispatcher) {
        val kelas = "XI RPL A"
        val date1 = fixedDate
        val date2 = "2026-09-13"
        val users = (1..3).map { makeUser("u$it", "S $it", kelas) }
        val presensiDate1 = listOf(makePresensi("u1", kelas))
        val presensiDate2 = listOf(makePresensi("u1", kelas, "06:50:00"), makePresensi("u2", kelas, "06:55:00")).map { it.copy(timestamp = it.timestamp.replace(fixedDate, date2)) }
        val repo = FakeTeacherRepo(
            usersByClass = mapOf(kelas to users),
            presensiByClassDate = mapOf(
                (kelas to date1) to presensiDate1,
                (kelas to date2) to presensiDate2,
            ),
        )
        val vm = viewModel(repo)
        val job = launch { vm.uiState.collect {} }
        runCurrent()
        assertEquals(1, vm.uiState.value.stats.totalHadir)

        vm.onDateSelected(date2)
        runCurrent()
        assertEquals(date2, vm.uiState.value.selectedDate)
        assertEquals(2, vm.uiState.value.stats.totalHadir)

        job.cancel()
    }
}
