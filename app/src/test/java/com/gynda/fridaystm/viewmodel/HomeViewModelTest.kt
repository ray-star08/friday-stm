package com.gynda.fridaystm.viewmodel

import com.gynda.fridaystm.R
import com.gynda.fridaystm.data.model.AttendanceRecord
import com.gynda.fridaystm.data.model.Geofence
import com.gynda.fridaystm.data.model.PembiasaanStamp
import com.gynda.fridaystm.data.model.RotationSchedule
import com.gynda.fridaystm.data.model.User
import com.gynda.fridaystm.domain.Activity
import com.gynda.fridaystm.domain.FridayPhase
import com.gynda.fridaystm.util.ActivityType
import com.gynda.fridaystm.util.LocationFix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

/**
 * Unit tests for [HomeViewModel] driven entirely by JVM fakes (see [TestDoubles]).
 *
 * Time is virtualized: one [StandardTestDispatcher] is installed as `Dispatchers.Main`
 * (so `viewModelScope` uses it) **and** passed to `runTest`, so both share a single
 * scheduler. That lets [advanceTimeBy] drive the ViewModel's ticking phase flow
 * deterministically — the 06:29 → 06:30 BEFORE→Pembiasaan transition is asserted
 * without any real wait.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    // 2026-08-14 is a Friday (matches the task.md examples).
    private val fridayBefore = LocalDateTime.of(2026, 8, 14, 6, 29)   // < 06:30 → BEFORE
    private val friday0630 = LocalDateTime.of(2026, 8, 14, 6, 30)     // → PEMBIASAAN
    private val fridayPembiasaan = LocalDateTime.of(2026, 8, 14, 6, 45)
    private val fridayCheckout = LocalDateTime.of(2026, 8, 14, 8, 10)

    // Week 33 → weekIndex 0 → grade 11 (slot 1) routes to LARKAM (index 1).
    private val week = 33
    private val student11 = User(
        uid = "u1",
        nis = "2024011",
        nama = "Budi",
        grade = 11,
        kelas = "XI RPL 1",
        role = "student",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        time: FakeTimeProvider,
        auth: FakeAuthRepository,
        attendance: FakeAttendanceRepository,
        geofences: FakeGeofenceRepository = FakeGeofenceRepository(),
        rotations: FakeRotationRepository = FakeRotationRepository(),
        location: FakeLocationProvider = FakeLocationProvider(),
    ) = HomeViewModel(time, auth, attendance, geofences, rotations, location)

    @Test
    fun `initial state is Loading before any subscriber`() {
        val vm = viewModel(
            time = FakeTimeProvider(fridayBefore, week),
            auth = FakeAuthRepository(mapOf("u1" to student11), initialUid = "u1"),
            attendance = FakeAttendanceRepository(),
        )

        // WhileSubscribed: with no collector, the StateFlow still exposes its seed value.
        assertEquals(HomeUiState.Loading, vm.uiState.value)
    }

    @Test
    fun `emits Loading then SignedOut then Ready as auth state resolves`() = runTest(dispatcher) {
        val auth = FakeAuthRepository(profiles = mapOf("u1" to student11), initialUid = null)
        val vm = viewModel(
            time = FakeTimeProvider(fridayBefore, week),
            auth = auth,
            attendance = FakeAttendanceRepository(),
        )

        val states = mutableListOf<HomeUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        runCurrent() // subscriber attaches → Loading, then upstream resolves signed-out → SignedOut
        auth.emitAuthState("u1") // sign in
        runCurrent() // profile + record combine → Ready

        val idxLoading = states.indexOfFirst { it is HomeUiState.Loading }
        val idxSignedOut = states.indexOfFirst { it is HomeUiState.SignedOut }
        val idxReady = states.indexOfFirst { it is HomeUiState.Ready }

        assertEquals("Loading must be the first state", 0, idxLoading)
        assertTrue("SignedOut must come after Loading", idxSignedOut in (idxLoading + 1) until idxReady)
        assertTrue("Ready must be the last transition", idxReady > idxSignedOut)
        assertEquals("u1", (states[idxReady] as HomeUiState.Ready).user.uid)

        job.cancel()
    }

    @Test
    fun `HomeAction auto-transitions from Before to Pembiasaan when the clock ticks past 0630`() =
        runTest(dispatcher) {
            val time = FakeTimeProvider(fridayBefore, week) // 06:29
            val vm = viewModel(
                time = time,
                auth = FakeAuthRepository(mapOf("u1" to student11), initialUid = "u1"),
                attendance = FakeAttendanceRepository(),
            )

            val job = launch { vm.uiState.collect {} } // keep the state flow warm
            runCurrent()

            // 06:29 → BEFORE phase, nothing to do yet.
            val atBefore = vm.uiState.value as HomeUiState.Ready
            assertEquals(FridayPhase.BEFORE, atBefore.phase)
            assertEquals(HomeAction.None, atBefore.action)

            // Advance the wall clock to 06:30, then let one ticker interval elapse.
            time.set(friday0630)
            advanceTimeBy(30_000) // PHASE_TICK_MS
            runCurrent()

            // The ticking flow re-resolves the phase on its own — no manual refresh.
            val atPembiasaan = vm.uiState.value as HomeUiState.Ready
            assertEquals(FridayPhase.PEMBIASAAN, atPembiasaan.phase)
            assertEquals(HomeAction.CheckInPembiasaan(Activity.LARKAM), atPembiasaan.action)

            job.cancel()
        }

    @Test
    fun `Pembiasaan already checked-in surfaces PembiasaanDone`() = runTest(dispatcher) {
        val record = AttendanceRecord(
            uid = "u1",
            date = "2026-08-14",
            grade = 11,
            pembiasaan = PembiasaanStamp(
                activity = ActivityType.LARKAM,
                checkedIn = true,
                time = "06:45",
            ),
        )
        val vm = viewModel(
            time = FakeTimeProvider(fridayPembiasaan, week),
            auth = FakeAuthRepository(mapOf("u1" to student11), initialUid = "u1"),
            attendance = FakeAttendanceRepository(initialRecord = record),
        )

        val job = launch { vm.uiState.collect {} }
        runCurrent()

        val ready = vm.uiState.value as HomeUiState.Ready
        assertEquals(HomeAction.PembiasaanDone(Activity.LARKAM), ready.action)

        job.cancel()
    }

    @Test
    fun `onCheckOut persists the stamp, reports Success, and flips action to CheckedOut`() =
        runTest(dispatcher) {
            val attendance = FakeAttendanceRepository()
            val vm = viewModel(
                time = FakeTimeProvider(fridayCheckout, week), // 08:10 → CHECKOUT
                auth = FakeAuthRepository(mapOf("u1" to student11), initialUid = "u1"),
                attendance = attendance,
            )

            val job = launch { vm.uiState.collect {} }
            runCurrent()

            assertEquals(HomeAction.CheckOut, (vm.uiState.value as HomeUiState.Ready).action)

            vm.onCheckOut()
            runCurrent()

            assertEquals(1, attendance.checkoutCalls.size)
            assertTrue(attendance.checkoutCalls.single().checkedOut)
            assertEquals(SubmitStatus.Success, vm.submitStatus.value)
            // The write flows back through observeTodayRecord → action becomes CheckedOut.
            assertEquals(HomeAction.CheckedOut, (vm.uiState.value as HomeUiState.Ready).action)

            job.cancel()
        }

    // 40 m Larkam fence at the campus reference point (task.md example).
    private val larkamFence = Geofence(
        id = "lapangan_utama",
        label = "Lapangan Utama",
        activity = ActivityType.LARKAM,
        lat = -6.87,
        lng = 107.54,
        radiusMeter = 40,
    )

    @Test
    fun `Pembiasaan target resolves and gate opens only when a real fix is inside the radius`() =
        runTest(dispatcher) {
            val location = FakeLocationProvider() // starts failing → no fix
            val vm = viewModel(
                time = FakeTimeProvider(fridayPembiasaan, week), // 06:45 → PEMBIASAAN (LARKAM)
                auth = FakeAuthRepository(mapOf("u1" to student11), initialUid = "u1"),
                attendance = FakeAttendanceRepository(),
                geofences = FakeGeofenceRepository(listOf(larkamFence)),
                location = location,
            )

            val job = launch { vm.uiState.collect {} }
            runCurrent()

            // No fix yet: target is known, but gate is closed and distance unknown.
            val noFix = vm.uiState.value as HomeUiState.Ready
            assertEquals(larkamFence, noFix.geofenceTarget)
            assertFalse(noFix.isInsideGeofence)
            assertNull(noFix.distanceMeters)

            // A real fix ~11 m north of center → inside a 40 m fence, gate opens.
            location.set(LocationFix(lat = -6.87 + 0.0001, lng = 107.54, isMock = false))
            advanceTimeBy(5_000) // LOCATION_POLL_MS
            runCurrent()

            val inside = vm.uiState.value as HomeUiState.Ready
            assertTrue(inside.isInsideGeofence)
            assertTrue("distance should be populated", (inside.distanceMeters ?: Double.MAX_VALUE) < 40.0)

            job.cancel()
        }

    @Test
    fun `mock-provided fix locks the screen into Error instead of a disabled Ready`() =
        runTest(dispatcher) {
            // Dead-center of the fence, but flagged as mock (fake-GPS).
            val location = FakeLocationProvider(LocationFix(lat = -6.87, lng = 107.54, isMock = true))
            val vm = viewModel(
                time = FakeTimeProvider(fridayPembiasaan, week),
                auth = FakeAuthRepository(mapOf("u1" to student11), initialUid = "u1"),
                attendance = FakeAttendanceRepository(),
                geofences = FakeGeofenceRepository(listOf(larkamFence)),
                location = location,
            )

            val job = launch { vm.uiState.collect {} }
            runCurrent()

            // Fail-closed AND loud: Error renders no action button at all, so every
            // check-in path is locked while mocking is on (task 4.2).
            val state = vm.uiState.value
            assertEquals(
                "mock location must surface an explicit Error state",
                HomeUiState.Error(R.string.location_mock_detected),
                state,
            )

            job.cancel()
        }

    @Test
    fun `a mock fix blocks the selfie submit even if one was already in flight`() =
        runTest(dispatcher) {
            val attendance = FakeAttendanceRepository()
            val location = FakeLocationProvider(
                LocationFix(lat = -6.87 + 0.0001, lng = 107.54, isMock = false),
            )
            val vm = viewModel(
                time = FakeTimeProvider(fridayPembiasaan, week),
                auth = FakeAuthRepository(mapOf("u1" to student11), initialUid = "u1"),
                attendance = attendance,
                geofences = FakeGeofenceRepository(listOf(larkamFence)),
                location = location,
            )

            val job = launch { vm.uiState.collect {} }
            runCurrent()
            assertTrue((vm.uiState.value as HomeUiState.Ready).isInsideGeofence)

            // Fake-GPS switched on while the camera flow was open.
            location.set(LocationFix(lat = -6.87, lng = 107.54, isMock = true))
            advanceTimeBy(5_000) // LOCATION_POLL_MS
            runCurrent()

            vm.onSelfieReady("https://res.cloudinary.com/demo/x.jpg")
            runCurrent()

            assertTrue("no write may reach the repository", attendance.pembiasaanCalls.isEmpty())

            job.cancel()
        }

    // --- Rotation: Firestore override vs cyclic fallback (task.md 3.2) ------

    private val senamFence = larkamFence.copy(
        id = "lapangan_basket",
        label = "Lapangan Basket",
        activity = ActivityType.SENAM,
    )

    /** Both fences, so whichever activity wins the rotation can resolve a target. */
    private fun bothFences() = FakeGeofenceRepository(listOf(larkamFence, senamFence))

    @Test
    fun `no rotation document falls back to the pure cyclic formula`() = runTest(dispatcher) {
        val rotations = FakeRotationRepository(schedule = null) // offline / not seeded
        val vm = viewModel(
            time = FakeTimeProvider(fridayPembiasaan, week),
            auth = FakeAuthRepository(mapOf("u1" to student11), initialUid = "u1"),
            attendance = FakeAttendanceRepository(),
            geofences = bothFences(),
            rotations = rotations,
        )

        val job = launch { vm.uiState.collect {} }
        runCurrent()

        // Week 33 → weekIndex 0 → grade 11 (slot 1) → LARKAM.
        val ready = vm.uiState.value as HomeUiState.Ready
        assertEquals(Activity.LARKAM, ready.activeActivity)
        assertFalse("cyclic fallback is not a special week", ready.isSpecialWeek)
        assertEquals(larkamFence, ready.geofenceTarget)
        assertEquals(HomeAction.CheckInPembiasaan(Activity.LARKAM), ready.action)
        // Looked up by the ISO week key, not a raw week number.
        assertEquals(listOf("2026-W33"), rotations.requestedWeekIds)

        job.cancel()
    }

    @Test
    fun `a Firestore rotation document overrides the cyclic formula for this grade`() =
        runTest(dispatcher) {
            val vm = viewModel(
                time = FakeTimeProvider(fridayPembiasaan, week),
                auth = FakeAuthRepository(mapOf("u1" to student11), initialUid = "u1"),
                attendance = FakeAttendanceRepository(),
                geofences = bothFences(),
                // Special week: grade 11 does SENAM, not the cyclic LARKAM.
                rotations = FakeRotationRepository(
                    RotationSchedule(
                        weekOfYear = week,
                        mapping = mapOf("11" to ActivityType.SENAM),
                    ),
                ),
            )

            val job = launch { vm.uiState.collect {} }
            runCurrent()

            val ready = vm.uiState.value as HomeUiState.Ready
            assertEquals(Activity.SENAM, ready.activeActivity)
            assertTrue("an applied override must flag the special week", ready.isSpecialWeek)
            // The target follows the override, so the gate guards the right place.
            assertEquals(senamFence, ready.geofenceTarget)
            assertEquals(HomeAction.CheckInPembiasaan(Activity.SENAM), ready.action)

            job.cancel()
        }

    @Test
    fun `an override without this grade or with a corrupt value falls back per grade`() =
        runTest(dispatcher) {
            val rotations = FakeRotationRepository(
                // Only grade 10 is pinned; grade 11 must keep the formula.
                RotationSchedule(weekOfYear = week, mapping = mapOf("10" to ActivityType.SENAM)),
            )
            val vm = viewModel(
                time = FakeTimeProvider(fridayPembiasaan, week),
                auth = FakeAuthRepository(mapOf("u1" to student11), initialUid = "u1"),
                attendance = FakeAttendanceRepository(),
                geofences = bothFences(),
                rotations = rotations,
            )

            val job = launch { vm.uiState.collect {} }
            runCurrent()
            (vm.uiState.value as HomeUiState.Ready).let {
                assertEquals(
                    "a missing grade key must not blank the activity",
                    Activity.LARKAM,
                    it.activeActivity,
                )
                assertFalse("an override that skips this grade is not special here", it.isSpecialWeek)
            }

            // Unparseable wire values degrade the same way — never to null.
            rotations.set(RotationSchedule(weekOfYear = week, mapping = mapOf("11" to "libur")))
            runCurrent()
            (vm.uiState.value as HomeUiState.Ready).let {
                assertEquals(
                    "a corrupt wire value must not blank the activity",
                    Activity.LARKAM,
                    it.activeActivity,
                )
                assertFalse("a corrupt override is not a special week", it.isSpecialWeek)
            }

            // `apel` is not a pembiasaan activity — activityFromWire rejects it too.
            rotations.set(
                RotationSchedule(weekOfYear = week, mapping = mapOf("11" to ActivityType.APEL)),
            )
            runCurrent()
            assertEquals(
                Activity.LARKAM,
                (vm.uiState.value as HomeUiState.Ready).activeActivity,
            )

            job.cancel()
        }

    @Test
    fun `retracting the override live returns the student to the cyclic activity`() =
        runTest(dispatcher) {
            val rotations = FakeRotationRepository(
                RotationSchedule(weekOfYear = week, mapping = mapOf("11" to ActivityType.SENAM)),
            )
            val vm = viewModel(
                time = FakeTimeProvider(fridayPembiasaan, week),
                auth = FakeAuthRepository(mapOf("u1" to student11), initialUid = "u1"),
                attendance = FakeAttendanceRepository(),
                geofences = bothFences(),
                rotations = rotations,
            )

            val job = launch { vm.uiState.collect {} }
            runCurrent()
            assertEquals(Activity.SENAM, (vm.uiState.value as HomeUiState.Ready).activeActivity)

            // Admin deletes the special-week doc; the stream emits null.
            rotations.set(null)
            runCurrent()

            val back = vm.uiState.value as HomeUiState.Ready
            assertEquals(Activity.LARKAM, back.activeActivity)
            assertEquals(larkamFence, back.geofenceTarget)

            job.cancel()
        }
}
