package com.gynda.fridaystm.viewmodel

import com.gynda.fridaystm.R
import com.gynda.fridaystm.data.model.AttendanceDay
import com.gynda.fridaystm.data.model.AttendanceDayStatus
import com.gynda.fridaystm.data.model.PresensiRecord
import com.gynda.fridaystm.data.repository.AttendanceReadRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun repository(stream: (String) -> Flow<List<AttendanceDay>>) =
        object : AttendanceReadRepository {
            override fun observeUser(userId: String) = stream(userId)
            override fun observeClass(kelas: String, date: String): Flow<List<AttendanceDay>> = error("unused")
            override suspend fun getUser(userId: String): Result<List<AttendanceDay>> = error("unused")
            override suspend fun getClass(kelas: String, startDate: String, endDate: String): Result<List<AttendanceDay>> = error("unused")
        }

    @Test fun legacyAndReviewDays_retainTheirEvidenceAndLifecycle() = runTest(dispatcher) {
        val legacy = AttendanceDay("first", "2026-09-18", AttendanceDayStatus.LEGACY,
            presensi = PresensiRecord(id = "legacy", userId = "first", timestamp = "2026-09-18T06:45:00"))
        val review = AttendanceDay("first", "2026-09-11", AttendanceDayStatus.NEEDS_REVIEW)
        val vm = HistoryViewModel(FakeAuthRepository(initialUid = "first"), repository { flowOf(listOf(legacy, review)) })
        backgroundScope.launch { vm.uiState.collect() }
        runCurrent()
        assertEquals(HistoryUiState.Success(listOf(legacy, review)), vm.uiState.value)
    }

    @Test fun accountSwitch_clearsOldRecordsBeforeNewQueryCompletes() = runTest(dispatcher) {
        val auth = FakeAuthRepository(initialUid = "first")
        val repo = repository { uid ->
            if (uid == "first") flowOf(listOf(AttendanceDay(uid, "2026-09-18", AttendanceDayStatus.PARTIAL)))
            else flow { awaitCancellation() }
        }
        val vm = HistoryViewModel(auth, repo)
        backgroundScope.launch { vm.uiState.collect() }
        runCurrent()
        auth.emitAuthState("second")
        runCurrent()
        assertEquals(HistoryUiState.Loading, vm.uiState.value)
    }

    @Test fun failedAccount_doesNotTerminateNextAccountSubscription() = runTest(dispatcher) {
        val auth = FakeAuthRepository(initialUid = "first")
        val repo = repository { uid ->
            if (uid == "first") flow { error("permission denied") }
            else flowOf(emptyList())
        }
        val vm = HistoryViewModel(auth, repo)
        backgroundScope.launch { vm.uiState.collect() }
        runCurrent()
        assertEquals(HistoryUiState.Error(R.string.history_error_generic), vm.uiState.value)
        auth.emitAuthState("second")
        runCurrent()
        assertEquals(HistoryUiState.Empty, vm.uiState.value)
    }
}
