package com.gynda.fridaystm.util

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.gynda.fridaystm.worker.PresensiSyncWorker
import java.util.concurrent.TimeUnit

/**
 * Scheduling seam for the presensi auto-sync, behind an interface so the
 * offline repository unit-tests with a recording fake instead of WorkManager.
 */
interface PresensiSyncScheduler {
    /** Enqueues (or keeps) the pending-presensi drain. No-op when already scheduled. */
    fun schedulePresensiSync()
}

/** [PresensiSyncScheduler] that delegates to [SyncManager] (WorkManager). */
class WorkManagerPresensiSyncScheduler(context: Context) : PresensiSyncScheduler {

    private val appContext = context.applicationContext

    override fun schedulePresensiSync() {
        SyncManager.schedulePresensiSync(appContext)
    }
}

/**
 * Helper around `WorkManager.enqueueUniqueWork` for draining the offline
 * presensi queue.
 *
 * - Unique work ([PRESENSI_SYNC_WORK]) with [ExistingWorkPolicy.KEEP]: every
 *   enqueue while offline collapses into one scheduled drain instead of
 *   stacking N workers.
 * - [Constraints] require `NetworkType.CONNECTED`, so the OS only runs the
 *   worker once a path exists — the worker itself never polls for signal.
 * - Exponential backoff (30 s base) spaces retries after failed attempts;
 *   per-item failures are additionally recorded as `FAILED` rows (see
 *   `PendingSyncStatus`) so no capture is silently dropped.
 */
object SyncManager {

    const val PRESENSI_SYNC_WORK = "presensi_sync"

    fun schedulePresensiSync(context: Context) {
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(PRESENSI_SYNC_WORK, ExistingWorkPolicy.KEEP, buildSyncRequest())
    }

    /** Cancels a scheduled drain (used after logout so no work outlives the session). */
    fun cancelPresensiSync(context: Context) {
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(PRESENSI_SYNC_WORK)
    }

    /** The constrained, backoff-configured request — also reused by tests/UI previews. */
    fun buildSyncRequest(): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<PresensiSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_MINUTES, TimeUnit.MINUTES)
            .addTag(PRESENSI_SYNC_WORK)
            .build()

    private const val BACKOFF_DELAY_MINUTES = 30L
}
