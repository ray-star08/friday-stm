package com.gynda.fridaystm.viewmodel

import com.gynda.fridaystm.data.local.PendingPresensiEntity
import com.gynda.fridaystm.data.local.PendingPresensiStore
import com.gynda.fridaystm.data.local.PendingSyncStatus
import com.gynda.fridaystm.util.PresensiSyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

    @Test
    fun terminalOnlyQueueDoesNotPromiseAutomaticResumption() = runTest(dispatcher) {
        val scheduler = RecordingScheduler()
        OfflineQueueViewModel(
            FakeAuthRepository(initialUid = "a"),
            Queue(status = PendingSyncStatus.NEEDS_ATTENTION),
            scheduler,
        )
        runCurrent()

        assertEquals("Terminal evidence cannot be retried automatically", 0, scheduler.resumed)
    }

    @Test fun terminalCountIsOwnerScopedAndClearedOnLogout() = runTest(dispatcher) {
        val auth = FakeAuthRepository(initialUid = "a")
        val vm = OfflineQueueViewModel(auth, Queue(PendingSyncStatus.NEEDS_ATTENTION), RecordingScheduler())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.needsAttentionCount.collect() }
        runCurrent()
        assertEquals(1, vm.needsAttentionCount.value)
        auth.signOut()
        runCurrent()
        assertEquals(0, vm.needsAttentionCount.value)
    }

    private class RecordingScheduler : PresensiSyncScheduler {
        var scheduled = 0
        var resumed = 0
        override fun schedulePresensiSync() { scheduled++ }
        override fun resumePresensiSync() { resumed++ }
    }

    @Test
    fun switchingOwnerClearsPreviousCountBeforeNewOwnerQueryReturns() = runTest(dispatcher) {
        val auth = FakeAuthRepository(initialUid = "a")
        val releaseNewOwner = CompletableDeferred<Unit>()
        val queue = object : Queue() {
            override fun observePendingCount(userId: String): Flow<Int> =
                if (userId == "b") flow { releaseNewOwner.await(); emit(0) }
                else super.observePendingCount(userId)
        }
        val viewModel = OfflineQueueViewModel(auth, queue, RecordingScheduler())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.pendingCount.collect() }
        runCurrent()
        assertEquals(1, viewModel.pendingCount.value)

        auth.emitAuthState("b")
        runCurrent()

        assertEquals("Old owner's queued evidence must not be shown while the next query loads", 0, viewModel.pendingCount.value)
        releaseNewOwner.complete(Unit)
        runCurrent()
    }

    private open class Queue(private val status: String = PendingSyncStatus.PENDING) : PendingPresensiStore {
        val counts = mapOf("a" to MutableStateFlow(1), "b" to MutableStateFlow(0))
        override fun observePendingCount(userId: String): Flow<Int> = counts.getValue(userId)
        override fun observeNeedsAttentionCount(userId: String): Flow<Int> = counts.getValue(userId).map {
            if (status == PendingSyncStatus.NEEDS_ATTENTION) it else 0
        }
        override suspend fun pendingList(): List<PendingPresensiEntity> = counts.flatMap { (uid, count) ->
            List(count.value) { index ->
                PendingPresensiEntity(
                    id = index + 1,
                    userId = uid,
                    timestampIso = "2026-09-18T07:00:00",
                    latitude = null,
                    longitude = null,
                    imagePath = "/synthetic/photo-$uid-$index.jpg",
                    storageFileName = "synthetic-$uid-$index.jpg",
                    studentName = "Fixture $uid",
                    studentClass = "XI Fixture",
                    statusSync = status,
                    createdAt = index.toLong(),
                )
            }
        }
        override suspend fun insert(entity: PendingPresensiEntity): Long = 0
        override suspend fun deleteById(id: Int) = Unit
        override suspend fun updateStatus(id: Int, status: String) = Unit
    }
}
