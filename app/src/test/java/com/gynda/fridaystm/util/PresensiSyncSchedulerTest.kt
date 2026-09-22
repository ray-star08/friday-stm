package com.gynda.fridaystm.util

import androidx.room.Room
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.impl.WorkDatabase
import androidx.work.impl.WorkManagerImpl
import com.gynda.fridaystm.data.local.PendingPresensiEntity
import com.gynda.fridaystm.data.local.PendingPresensiStore
import com.gynda.fridaystm.viewmodel.FakeAuthRepository
import com.gynda.fridaystm.viewmodel.OfflineQueueViewModel
import java.util.concurrent.Executor
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Exercises real WorkManager enqueue policies and its persisted dependency graph. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PresensiSyncSchedulerTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var database: WorkDatabase
    private lateinit var workManager: WorkManagerImpl
    private lateinit var scheduler: WorkManagerPresensiSyncScheduler

    @Before
    fun before() {
        Dispatchers.setMain(dispatcher)
        val context = RuntimeEnvironment.getApplication()
        val directExecutor = Executor { it.run() }
        database = Room.inMemoryDatabaseBuilder(context, WorkDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val configuration = Configuration.Builder()
            .setExecutor(directExecutor)
            .setTaskExecutor(directExecutor)
            .build()
        workManager = WorkManagerImpl(
            context = context,
            configuration = configuration,
            workDatabase = database,
            // Exercise enqueueing, not Firebase/network workers or OS scheduling.
            schedulersCreator = { _, _, _, _, _, _ -> emptyList() },
        )
        WorkManagerImpl.setDelegate(workManager)
        scheduler = WorkManagerPresensiSyncScheduler(context)
    }

    @After
    fun after() {
        WorkManagerImpl.setDelegate(null)
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun returningOwnerQueuesFollowUpBehindRunningForeignUploadWithoutCancellingIt() = runTest(dispatcher) {
        val running = SyncManager.buildSyncRequest()
        workManager.enqueueUniqueWork(
            SyncManager.PRESENSI_SYNC_WORK,
            ExistingWorkPolicy.KEEP,
            running,
        ).result.get()
        // Hold the foreign-account upload at the WorkManager RUNNING boundary.
        // No cancellation-cooperative fake can hide a REPLACE/concurrent-drain bug.
        database.workSpecDao().setState(WorkInfo.State.RUNNING, running.id.toString())
        val auth = FakeAuthRepository(initialUid = "b")
        val queue = Queue()
        OfflineQueueViewModel(auth, queue, scheduler)
        runCurrent()

        auth.emitAuthState("a")
        runCurrent()

        val work = workManager.getWorkInfosForUniqueWork(SyncManager.PRESENSI_SYNC_WORK).get()
        assertEquals("The returning owner's request must survive a running foreign upload", 2, work.size)
        val followUp = work.single { it.id != running.id }
        assertEquals(WorkInfo.State.RUNNING, work.single { it.id == running.id }.state)
        assertFalse(workManager.processor.isCancelled(running.id.toString()))
        assertEquals(WorkInfo.State.BLOCKED, followUp.state)
        assertEquals(
            listOf(running.id.toString()),
            database.dependencyDao().getPrerequisites(followUp.id.toString()),
        )
        assertFalse(database.dependencyDao().hasCompletedAllPrerequisites(followUp.id.toString()))
        assertEquals(
            NetworkType.CONNECTED,
            database.workSpecDao().getWorkSpec(followUp.id.toString())!!.constraints.requiredNetworkType,
        )
        // Finishing the old drain satisfies the persisted follow-up's prerequisite.
        database.workSpecDao().setState(WorkInfo.State.SUCCEEDED, running.id.toString())
        assertTrue(database.dependencyDao().hasCompletedAllPrerequisites(followUp.id.toString()))
    }

    @Test
    fun ordinarySchedulingAndManualRetryKeepTheRunningDrain() = runTest(dispatcher) {
        scheduler.schedulePresensiSync()
        val original = workManager.getWorkInfosForUniqueWork(SyncManager.PRESENSI_SYNC_WORK).get().single()
        database.workSpecDao().setState(WorkInfo.State.RUNNING, original.id.toString())
        val viewModel = OfflineQueueViewModel(FakeAuthRepository(), Queue(), scheduler)
        runCurrent()

        scheduler.schedulePresensiSync()
        viewModel.syncNow()
        viewModel.syncNow()

        val work = workManager.getWorkInfosForUniqueWork(SyncManager.PRESENSI_SYNC_WORK).get()
        assertEquals(listOf(original.id), work.map { it.id })
        assertEquals(WorkInfo.State.RUNNING, work.single().state)
        assertFalse(workManager.processor.isCancelled(original.id.toString()))
    }

    @Test
    fun resumptionStartsFreshAfterAnAlreadyFailedChain() {
        assertResumptionStartsFreshAfter(WorkInfo.State.FAILED)
    }

    @Test
    fun resumptionStartsFreshAfterAnAlreadyCancelledChain() {
        assertResumptionStartsFreshAfter(WorkInfo.State.CANCELLED)
    }

    private fun assertResumptionStartsFreshAfter(terminalState: WorkInfo.State) {
        scheduler.schedulePresensiSync()
        val previous = workManager.getWorkInfosForUniqueWork(SyncManager.PRESENSI_SYNC_WORK).get().single()
        database.workSpecDao().setState(terminalState, previous.id.toString())

        scheduler.resumePresensiSync()

        val work = workManager.getWorkInfosForUniqueWork(SyncManager.PRESENSI_SYNC_WORK).get()
        val resumed = work.single()
        assertFalse(previous.id == resumed.id)
        assertEquals(WorkInfo.State.ENQUEUED, resumed.state)
        assertTrue(database.dependencyDao().getPrerequisites(resumed.id.toString()).isEmpty())
    }

    private class Queue : PendingPresensiStore {
        override fun observePendingCount(userId: String): Flow<Int> =
            MutableStateFlow(if (userId == "a") 1 else 0)
        override suspend fun pendingList(): List<PendingPresensiEntity> = emptyList()
        override suspend fun insert(entity: PendingPresensiEntity): Long = 0
        override suspend fun deleteById(id: Int) = Unit
        override suspend fun updateStatus(id: Int, status: String) = Unit
    }
}
