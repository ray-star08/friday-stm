package com.gynda.fridaystm.data.repository

import com.google.firebase.FirebaseNetworkException
import com.gynda.fridaystm.data.local.PendingPresensiEntity
import com.gynda.fridaystm.data.local.PendingPresensiStore
import com.gynda.fridaystm.data.local.PendingSyncStatus
import com.gynda.fridaystm.util.NetworkMonitor
import com.gynda.fridaystm.util.PresensiSyncScheduler
import com.gynda.fridaystm.util.TimeProvider
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Outcome of [OfflinePresensiRepository.submitPresensi] as seen by callers:
 * either the capture is fully persisted remotely, or it is safely queued.
 */
sealed interface PresensiSubmitResult {
    /** Uploaded to Storage and written to Firestore in this call. */
    data class Uploaded(val imageUrl: String) : PresensiSubmitResult

    /** No connectivity (or a network failure): JPEG cached, Room row enqueued, sync scheduled. */
    data object QueuedOffline : PresensiSubmitResult
}

/**
 * Outcome of the upload-only step shared by [submitPresensi] and
 * `PresensiCameraViewModel`'s watermark flow.
 */
sealed interface PresensiUploadResult {
    data class Uploaded(val imageUrl: String) : PresensiUploadResult
    data object QueuedOffline : PresensiUploadResult
}

/**
 * Offline-first presensi persistence.
 *
 * - Online: JPEG bytes → Firebase Storage, then the Firestore document —
 *   identical payload to the legacy path.
 * - Offline (or a network exception mid-upload): JPEG → app cache file,
 *   metadata → Room (`PENDING`), then [PresensiSyncScheduler] enqueues the
 *   constrained drain ([SyncManager][com.gynda.fridaystm.util.SyncManager]).
 *
 * Non-network failures (blank user, empty bytes, Firestore permission /
 * validation errors while online) are returned as failures and never queued —
 * retrying them would only burn battery.
 */
interface OfflinePresensiRepository {

    suspend fun submitPresensi(
        userId: String,
        timestamp: LocalDateTime,
        imageBytes: ByteArray,
        lat: Double?,
        lng: Double?,
        studentName: String,
        studentClass: String,
    ): Result<PresensiSubmitResult>

    /**
     * Upload step only (no Firestore write): used by flows that must inspect
     * the URL first (e.g. the Larkam `larkam_records` branch). Offline queues
     * the capture with the caller-supplied record fields.
     */
    suspend fun uploadOrQueue(
        userId: String,
        timestamp: LocalDateTime,
        imageBytes: ByteArray,
        lat: Double?,
        lng: Double?,
        studentName: String,
        studentClass: String,
    ): Result<PresensiUploadResult>
}

class OfflineFirstPresensiRepository(
    private val authRepository: AuthRepository,
    private val networkMonitor: NetworkMonitor,
    private val queue: PendingPresensiStore,
    private val photoCache: PendingPhotoCache,
    private val storageRepository: StorageRepository,
    private val presensiRepository: PresensiRepository,
    private val syncScheduler: PresensiSyncScheduler,
    private val timeProvider: TimeProvider,
) : OfflinePresensiRepository {

    override suspend fun submitPresensi(
        userId: String,
        timestamp: LocalDateTime,
        imageBytes: ByteArray,
        lat: Double?,
        lng: Double?,
        studentName: String,
        studentClass: String,
    ): Result<PresensiSubmitResult> = runCatching {
        when (
            val upload = uploadOrQueue(
                userId, timestamp, imageBytes, lat, lng, studentName, studentClass,
            ).getOrThrow()
        ) {
            is PresensiUploadResult.QueuedOffline -> PresensiSubmitResult.QueuedOffline
            is PresensiUploadResult.Uploaded -> {
                check(authRepository.currentUid == userId) { "Account changed during upload" }
                presensiRepository.savePresensi(
                    userId = userId,
                    timestamp = timestamp,
                    imageUrl = upload.imageUrl,
                    lat = lat,
                    lng = lng,
                    studentName = studentName,
                    studentClass = studentClass,
                ).getOrThrow()
                PresensiSubmitResult.Uploaded(upload.imageUrl)
            }
        }
    }

    override suspend fun uploadOrQueue(
        userId: String,
        timestamp: LocalDateTime,
        imageBytes: ByteArray,
        lat: Double?,
        lng: Double?,
        studentName: String,
        studentClass: String,
    ): Result<PresensiUploadResult> = runCatching {
        require(userId.isNotBlank()) { "userId must not be blank" }
        check(authRepository.currentUid == userId) { "Presensi requires the current signed-in owner" }
        require(imageBytes.isNotEmpty()) { "photo bytes must not be empty" }

        if (!networkMonitor.isOnline()) {
            return@runCatching enqueueOffline(
                userId, timestamp, imageBytes, lat, lng, studentName, studentClass,
            ).getOrThrow()
        }
        try {
            val url = storageRepository.uploadPresensiBytes(
                userId = userId,
                bytes = imageBytes,
                storageFileName = FirebaseStorageRepository.presensiFileName(userId, timestamp),
            ).getOrThrow()
            check(authRepository.currentUid == userId) { "Account changed during upload" }
            PresensiUploadResult.Uploaded(url)
        } catch (e: Exception) {
            // Connectivity dropped between the check and the put, or the
            // backend was unreachable: queue instead of failing when the
            // device is (now) offline or the error is network-caused.
            if (!networkMonitor.isOnline() || e.isNetworkError()) {
                enqueueOffline(
                    userId, timestamp, imageBytes, lat, lng, studentName, studentClass,
                ).getOrThrow()
            } else {
                throw e
            }
        }
    }

    private suspend fun enqueueOffline(
        userId: String,
        timestamp: LocalDateTime,
        imageBytes: ByteArray,
        lat: Double?,
        lng: Double?,
        studentName: String,
        studentClass: String,
    ): Result<PresensiUploadResult> = runCatching {
        check(authRepository.currentUid == userId) { "Account changed before queueing" }
        val imagePath = photoCache.savePendingPhoto(userId, timestamp, imageBytes).getOrThrow()
        try {
            queue.insert(
                PendingPresensiEntity(
                    userId = userId,
                    timestampIso = timestamp.format(ISO_FORMATTER),
                    latitude = lat,
                    longitude = lng,
                    imagePath = imagePath,
                    storageFileName = FirebaseStorageRepository.presensiFileName(userId, timestamp),
                    studentName = studentName,
                    studentClass = studentClass,
                    statusSync = PendingSyncStatus.PENDING,
                    createdAt = timeProvider.now().toEpochMilli(),
                ),
            )
        } catch (e: Exception) {
            // Don't orphan a cache file the queue can't see.
            photoCache.deletePhoto(imagePath)
            throw e
        }
        syncScheduler.schedulePresensiSync()
        PresensiUploadResult.QueuedOffline
    }

    companion object {
        private val ISO_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    }
}

/** Walks an exception chain looking for transport failures (socket, timeout, Firebase network). */
private fun Throwable.isNetworkError(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is IOException || current is FirebaseNetworkException) return true
        current = current.cause
    }
    return false
}

private fun LocalDateTime.toEpochMilli(): Long =
    atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
