package com.gynda.fridaystm.data.repository

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.gynda.fridaystm.data.local.PendingPresensiEntity
import com.gynda.fridaystm.data.local.PendingPresensiStore
import com.gynda.fridaystm.data.local.PendingSyncStatus
import com.gynda.fridaystm.data.model.CaptureDraft
import com.gynda.fridaystm.data.model.LarkamCapture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDateTime
import java.util.UUID

/** Serializes UI submissions with the WorkManager drain within the application process. */
internal object CaptureOutboxLock { val mutex = Mutex() }
internal class CaptureOwnerChanged : IllegalStateException("Akun berubah. Bukti tersimpan untuk pemilik semula.")
internal class MissingCapturePhoto : IllegalStateException("Foto antrean hilang. Ambil ulang atau hubungi admin.")
internal fun requireCaptureOwner(auth: AuthRepository, uid: String) {
    if (auth.currentUid != uid) throw CaptureOwnerChanged()
}

internal fun CaptureDraft.snapshotAndValidate(): CaptureDraft {
    require(captureId.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))) { "ID capture tidak valid" }
    require(userId.isNotBlank() && studentName.isNotBlank() && studentClass.isNotBlank()) { "Identitas capture tidak lengkap" }
    require((lat == null && lng == null) || (lat != null && lng != null && lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0)) { "Koordinat capture tidak valid" }
    val run = larkam?.let { value ->
        require(value.distanceKm.isFinite() && value.distanceKm >= 0 && value.durationSeconds >= 0) { "Statistik Larkam tidak valid" }
        require(value.route.size <= 10000) { "Rute Larkam terlalu panjang" }
        value.copy(route = value.route.map { point ->
            val latitude = requireNotNull(point["lat"])
            val longitude = requireNotNull(point["lng"])
            require(latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0)
            mapOf("lat" to latitude, "lng" to longitude)
        })
    }
    return copy(larkam = run)
}

internal fun PendingPresensiEntity.toCaptureDraft(): CaptureDraft {
    require(captureKind == "GENERIC" || captureKind == "LARKAM") { "Jenis capture tidak dikenal" }
    val run = if (captureKind == "LARKAM") LarkamCapture(
        requireNotNull(larkamDistanceKm), requireNotNull(larkamDurationSeconds),
        requireNotNull(larkamRoute).let { encoded ->
            if (encoded.isEmpty()) emptyList() else encoded.split(';').map { token ->
                val values = token.split(',')
                require(values.size == 2)
                mapOf("lat" to values[0].toDouble(), "lng" to values[1].toDouble())
            }
        },
    ) else null
    val stableId = captureId.ifBlank {
        UUID.nameUUIDFromBytes("legacy:$userId:$timestampIso:$id".toByteArray(Charsets.UTF_8)).toString()
    }
    return CaptureDraft(stableId, userId, LocalDateTime.parse(timestampIso), latitude, longitude, studentName, studentClass, run).snapshotAndValidate()
}

internal fun Throwable.retryableCaptureFailure(): Boolean = when (this) {
    is IOException, is FirebaseNetworkException -> true
    is FirebaseFirestoreException -> code in setOf(
        FirebaseFirestoreException.Code.UNAVAILABLE, FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
        FirebaseFirestoreException.Code.ABORTED, FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED,
        FirebaseFirestoreException.Code.INTERNAL, FirebaseFirestoreException.Code.UNKNOWN,
    )
    else -> false
}

/** Caller holds [CaptureOutboxLock]. Upload receipt is durable before document creation. */
internal suspend fun commitQueuedCapture(
    row: PendingPresensiEntity,
    auth: AuthRepository,
    queue: PendingPresensiStore,
    photos: PendingPhotoCache,
    storage: StorageRepository,
    writer: CaptureWriter,
): String {
    val draft = row.toCaptureDraft()
    requireCaptureOwner(auth, row.userId)
    val url = row.uploadedImageUrl ?: run {
        val bytes = withContext(Dispatchers.IO) { photos.readPhoto(row.imagePath) } ?: throw MissingCapturePhoto()
        requireCaptureOwner(auth, row.userId)
        val uploaded = storage.uploadPresensiBytes(row.userId, bytes, "capture_${draft.captureId}").getOrThrow()
        requireCaptureOwner(auth, row.userId)
        queue.checkpointUpload(row.id, uploaded)
        uploaded
    }
    requireCaptureOwner(auth, row.userId)
    writer.saveCapture(draft, url).getOrThrow()
    requireCaptureOwner(auth, row.userId)
    try {
        queue.deleteById(row.id)
    } finally {
        withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
            // Cancellation may arrive after the DELETE committed. Clean only
            // after proving no surviving queue row still owns this local file.
            val unreferenced = runCatching {
                queue.pendingList().none { it.imagePath == row.imagePath }
            }.getOrDefault(false)
            if (unreferenced) photos.deletePhoto(row.imagePath)
        }
    }
    return url
}

internal suspend fun markCaptureFailure(queue: PendingPresensiStore, row: PendingPresensiEntity, error: Exception) {
    if (error is CancellationException || error is CaptureOwnerChanged) return
    queue.updateStatus(row.id, if (error.retryableCaptureFailure()) PendingSyncStatus.FAILED else PendingSyncStatus.NEEDS_ATTENTION)
}
