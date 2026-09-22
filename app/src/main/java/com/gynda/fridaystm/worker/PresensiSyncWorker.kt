package com.gynda.fridaystm.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gynda.fridaystm.data.local.AppDatabase
import com.gynda.fridaystm.data.local.RoomPendingPresensiStore
import com.gynda.fridaystm.data.repository.AppPendingPhotoCache
import com.gynda.fridaystm.data.repository.CloudinaryStorageRepository
import com.gynda.fridaystm.data.repository.FirebasePresensiRepository
import com.gynda.fridaystm.data.repository.PendingPresensiSyncer

/**
 * Background drain of the offline presensi queue.
 *
 * Scheduled via [SyncManager][com.gynda.fridaystm.util.SyncManager] with
 * `NetworkType.CONNECTED` + exponential backoff; each enqueue uses
 * `ExistingWorkPolicy.KEEP`, so N offline captures collapse into a single
 * worker run that iterates the whole queue.
 *
 * Mapping: empty/all-synced → `Result.success()`; any `FAILED` row left →
 * `Result.retry()` (backoff-configured, rows keep their files for the next
 * attempt). Collaborators are built from the [AppDatabase] singleton +
 * Firebase defaults — the drain logic itself lives in the unit-tested
 * [PendingPresensiSyncer].
 */
class PresensiSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val syncer = PendingPresensiSyncer(
            authRepository = com.gynda.fridaystm.data.repository.FirebaseAuthRepository(),
            queue = RoomPendingPresensiStore(AppDatabase.get(context).pendingPresensiDao()),
            photoCache = AppPendingPhotoCache(context),
            storageRepository = CloudinaryStorageRepository(),
            presensiRepository = FirebasePresensiRepository(),
            captureWriter = com.gynda.fridaystm.data.repository.FirebaseCaptureWriter(),
        )
        return try {
            val summary = syncer.syncPending()
            if (summary.hasFailures) Result.retry() else Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
