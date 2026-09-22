package com.gynda.fridaystm.data.repository

import com.gynda.fridaystm.data.local.PendingPresensiStore
import com.gynda.fridaystm.data.local.PendingSyncStatus
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Tally of one queue drain, reported by [PendingPresensiSyncer.syncPending]. */
data class PresensiSyncSummary(
    val synced: Int,
    val failed: Int,
) {
    val hasFailures: Boolean get() = failed > 0
}

/**
 * Drains the offline presensi queue: for each row, cache file → Storage
 * upload → Firestore write → row + file deletion.
 *
 * Pure orchestration over interfaces (no `Context`, no WorkManager types), so
 * it unit-tests on the JVM with fakes; [PresensiSyncWorker][com.gynda.fridaystm.worker.PresensiSyncWorker]
 * is a thin host that builds the real collaborators and maps [PresensiSyncSummary]
 * to `Worker.Result`.
 *
 * Failure policy per item (never aborts the drain):
 * - missing/unreadable cache file or unparseable timestamp → `FAILED`
 *   (the capture can no longer be completed; the row is kept as evidence
 *   instead of being silently dropped);
 * - upload/save exception → `FAILED`, picked up again by the WorkManager
 *   exponential-backoff retry.
 */
class PendingPresensiSyncer(
    private val authRepository: AuthRepository,
    private val queue: PendingPresensiStore,
    private val photoCache: PendingPhotoCache,
    private val storageRepository: StorageRepository,
    private val presensiRepository: PresensiRepository,
    private val captureWriter: CaptureWriter? = null,
) {

    suspend fun syncPending(): PresensiSyncSummary {
        val ownerUid = authRepository.currentUid ?: return PresensiSyncSummary(0, 0)
        var synced = 0
        var failed = 0
        for (entity in queue.pendingList()) {
            if (authRepository.currentUid != ownerUid) break
            if (entity.userId != ownerUid) continue
            if (captureWriter != null) {
                if (entity.statusSync == PendingSyncStatus.NEEDS_ATTENTION) continue
                CaptureOutboxLock.mutex.lock()
                try {
                    val fresh = queue.pendingList().firstOrNull { it.id == entity.id } ?: continue
                    if (fresh.statusSync == PendingSyncStatus.NEEDS_ATTENTION) continue
                    commitQueuedCapture(fresh, authRepository, queue, photoCache, storageRepository, captureWriter)
                    synced++
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: CaptureOwnerChanged) {
                    break
                } catch (error: Exception) {
                    markCaptureFailure(queue, entity, error)
                    if (error.retryableCaptureFailure()) failed++
                } finally {
                    CaptureOutboxLock.mutex.unlock()
                }
                continue
            }
            try {
                check(entity.captureKind == "GENERIC") { "Typed capture writer required for Larkam" }
                val bytes = photoCache.readPhoto(entity.imagePath)
                    ?: throw IllegalStateException("Cached photo missing: ${entity.imagePath}")
                val timestamp = try {
                    LocalDateTime.parse(entity.timestampIso, ISO_FORMATTER)
                } catch (e: Exception) {
                    throw IllegalStateException("Unparseable timestamp: ${entity.timestampIso}", e)
                }
                val imageUrl = storageRepository.uploadPresensiBytes(
                    userId = entity.userId,
                    bytes = bytes,
                    storageFileName = entity.storageFileName,
                ).getOrThrow()
                if (authRepository.currentUid != ownerUid) break
                presensiRepository.savePresensi(
                    userId = entity.userId,
                    timestamp = timestamp,
                    imageUrl = imageUrl,
                    lat = entity.latitude,
                    lng = entity.longitude,
                    studentName = entity.studentName,
                    studentClass = entity.studentClass,
                ).getOrThrow()
                queue.deleteById(entity.id)
                photoCache.deletePhoto(entity.imagePath)
                synced++
            } catch (_: Exception) {
                if (authRepository.currentUid != ownerUid) break
                try {
                    queue.updateStatus(entity.id, PendingSyncStatus.FAILED)
                } catch (_: Exception) {
                    // Status write itself failed — still count the item as failed.
                }
                failed++
            }
        }
        return PresensiSyncSummary(synced = synced, failed = failed)
    }

    companion object {
        private val ISO_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    }
}
