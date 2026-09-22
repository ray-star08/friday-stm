package com.gynda.fridaystm.viewmodel

import com.gynda.fridaystm.data.local.PendingPresensiEntity
import com.gynda.fridaystm.data.local.PendingPresensiStore
import com.gynda.fridaystm.util.PresensiSyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
class OfflineQueueViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }

    @Test
    fun returningOwnerAutomaticallyResumesOwnPendingRows() = runTest(dispatcher) {
        val auth = FakeAuthRepository(initialUid = "a")
        val scheduler = RecordingScheduler()
        OfflineQueueViewModel(auth, Queue(), scheduler)
        runCurrent()
        assertEquals(1, scheduler.resumed)
        auth.signOut()
        runCurrent()
        assertEquals(1, scheduler.resumed)
        auth.emitAuthState("b")
        runCurrent()
        assertEquals(1, scheduler.resumed)
        auth.emitAuthState("a")
        runCurrent()
        assertEquals(2, scheduler.resumed)
        assertEquals("Auth resumption must not use the lossy KEEP operation", 0, scheduler.scheduled)
    }

    @Test
    fun pendingCountChangesDoNotRepeatedlyResumeWithinTheSameAuthSession() = runTest(dispatcher) {
        val auth = FakeAuthRepository(initialUid = "a")
        val queue = Queue()
        val scheduler = RecordingScheduler()
        OfflineQueueViewModel(auth, queue, scheduler)
        runCurrent()
        assertEquals(1, scheduler.resumed)

        for (count in listOf(2, 1, 0, 1, 3)) {
            queue.counts.getValue("a").value = count
            runCurrent()
        }
        auth.emitAuthState("a")
        runCurrent()
        assertEquals("A failed or newly inserted row must not create a resumption loop", 1, scheduler.resumed)
        assertEquals(0, scheduler.scheduled)
    }

    @Test
    fun ownerResumptionWaitsForFirstNonEmptyQueueEmission() = runTest(dispatcher) {
        val auth = FakeAuthRepository(initialUid = "b")
        val queue = Queue()
        val scheduler = RecordingScheduler()
        OfflineQueueViewModel(auth, queue, scheduler)
        runCurrent()
        assertEquals(0, scheduler.resumed)

        queue.counts.getValue("b").value = 1
        runCurrent()
        assertEquals(1, scheduler.resumed)
        queue.counts.getValue("b").value = 2
        runCurrent()
        assertEquals(1, scheduler.resumed)
        assertEquals(0, scheduler.scheduled)
    }

    @Test
    fun emptyQueueAndSignedOutDoNotSchedule() = runTest(dispatcher) {
        val auth = FakeAuthRepository()
        val scheduler = RecordingScheduler()
        OfflineQueueViewModel(auth, Queue(), scheduler)
        runCurrent()
        auth.emitAuthState("b")
        runCurrent()
        assertEquals(0, scheduler.resumed)
        assertEquals(0, scheduler.scheduled)
    }

    @Test
    fun manualRetryUsesOrdinarySchedulingNotAuthResumption() = runTest(dispatcher) {
        val scheduler = RecordingScheduler()
        val viewModel = OfflineQueueViewModel(FakeAuthRepository(), Queue(), scheduler)
        runCurrent()

        viewModel.syncNow()

        assertEquals(1, scheduler.scheduled)
        assertEquals(0, scheduler.resumed)
    }

    private class RecordingScheduler : PresensiSyncScheduler {
        var scheduled = 0
        var resumed = 0
        override fun schedulePresensiSync() { scheduled++ }
        override fun resumePresensiSync() { resumed++ }
    }

    private class Queue : PendingPresensiStore {
        val counts = mapOf("a" to MutableStateFlow(1), "b" to MutableStateFlow(0))
        override fun observePendingCount(userId: String): Flow<Int> = counts.getValue(userId)
        override suspend fun pendingList(): List<PendingPresensiEntity> = emptyList()
        override suspend fun insert(entity: PendingPresensiEntity): Long = 0
        override suspend fun deleteById(id: Int) = Unit
        override suspend fun updateStatus(id: Int, status: String) = Unit
    }
}
