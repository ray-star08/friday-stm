package com.gynda.fridaystm.viewmodel

import android.graphics.Bitmap
import android.location.Location
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gynda.fridaystm.data.repository.AuthRepository
import com.gynda.fridaystm.data.repository.CloudinaryStorageRepository
import com.gynda.fridaystm.data.repository.FirebaseAuthRepository
import com.gynda.fridaystm.data.repository.FirebasePresensiRepository
import com.gynda.fridaystm.data.repository.OfflinePresensiRepository
import com.gynda.fridaystm.data.repository.PresensiRepository
import com.gynda.fridaystm.data.repository.PresensiUploadResult
import com.gynda.fridaystm.data.repository.StorageRepository
import com.gynda.fridaystm.util.toCompressJpegByteArray
import com.gynda.fridaystm.util.LocationProvider
import com.gynda.fridaystm.util.FusedLocationProvider
import com.gynda.fridaystm.util.LarkamPayloadHolder
import com.gynda.fridaystm.util.MAX_RADIUS_METERS
import com.gynda.fridaystm.util.SystemTimeProvider
import com.gynda.fridaystm.util.TimeProvider
import com.gynda.fridaystm.util.addLarkamWatermark
import com.gynda.fridaystm.util.addPresensiWatermark
import com.gynda.fridaystm.util.calculateDistanceToSchool
import com.gynda.fridaystm.util.isWithinSchoolRadius
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/**
 * Handles full presensi selfie pipeline:
 * ImageProxy -> Bitmap -> Watermark (Nama, Kelas, Lokasi, Timestamp) -> Storage Upload -> Firestore save.
 *
 * ImageProxy is always closed in finally to avoid camera memory leak.
 */
class PresensiCameraViewModel(
    private val storageRepository: StorageRepository,
    private val presensiRepository: PresensiRepository,
    private val locationProvider: LocationProvider,
    private val authRepository: AuthRepository,
    private val timeProvider: TimeProvider,
    // For testability: converter can be overridden to avoid real YUV decoding on JVM.
    private val imageProxyConverter: suspend (ImageProxy) -> Bitmap = { proxy -> proxy.toBitmapCompat() },
    /**
     * Offline-first upload seam. `null` (unit tests, legacy callers) keeps the
     * previous direct-upload behavior; production passes an
     * [OfflineFirstPresensiRepository][com.gynda.fridaystm.data.repository.OfflineFirstPresensiRepository]
     * so a dead network queues the capture instead of erroring.
     */
    private val offlineRepository: OfflinePresensiRepository? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow<PresensiCameraUiState>(PresensiCameraUiState.Idle)
    val uiState: StateFlow<PresensiCameraUiState> = _uiState.asStateFlow()

    /**
     * Main entry: called from CameraX capture callback.
     * Handles watermark + upload + firestore atomically.
     */
    fun onImageCaptured(imageProxy: ImageProxy) {
        viewModelScope.launch {
            _uiState.value = PresensiCameraUiState.Loading
            var watermarked: Bitmap? = null
            try {
                // 1. Resolve user session
                val uid = authRepository.currentUid
                    ?: throw IllegalStateException("User not logged in")
                val userResult = authRepository.getUserProfile(uid)
                val user = userResult.getOrElse { throw it }
                val studentName = user.nama.ifBlank { "Siswa" }
                val studentClass = user.kelas.ifBlank { "-" }

                // 2. Get location — presensi requires valid location; watermark can handle null but business rule treats null as error for testability.
                val locationFixResult = locationProvider.currentLocation()
                if (locationFixResult.isFailure) {
                    throw IllegalStateException("Lokasi tidak tersedia")
                }
                val fix = locationFixResult.getOrNull()!!
                // Geofencing: must be within school radius
                if (!isWithinSchoolRadius(fix.lat, fix.lng)) {
                    val jarak = calculateDistanceToSchool(fix.lat, fix.lng)
                    throw IllegalStateException("Di luar area sekolah! Jarak Anda: ${jarak.toInt()} meter dari sekolah.")
                }
                val location: Location? = Location("fused").apply {
                    latitude = fix.lat
                    longitude = fix.lng
                }

                // 3. Convert ImageProxy -> Bitmap (with proper close handling in finally)
                val originalBitmap = imageProxyConverter(imageProxy)

                // 4. Apply watermark efficiently (handles scaling & recycle)
                // If Larkam payload exists, use Larkam watermark with stats
                val timestamp = timeProvider.now()
                val larkamPayload = if (LarkamPayloadHolder.hasPayload()) LarkamPayloadHolder else null
                watermarked = if (larkamPayload != null) {
                    addLarkamWatermark(
                        bitmap = originalBitmap,
                        studentName = studentName,
                        studentClass = studentClass,
                        location = location,
                        timestamp = timestamp,
                        totalDistanceKm = larkamPayload.distanceKm ?: 0f,
                        durationFormatted = larkamPayload.durationFormatted ?: "--:--"
                    )
                } else {
                    addPresensiWatermark(
                        bitmap = originalBitmap,
                        studentName = studentName,
                        studentClass = studentClass,
                        location = location,
                        timestamp = timestamp
                    )
                }

                // 5. Upload to Firebase Storage (uses toCompressJpegByteArray(80) internally).
                // Offline-first when wired: dead network queues the capture (Room + scheduled sync).
                val locFixForUpload = locationFixResult.getOrNull()
                val downloadUrl = uploadSelfie(
                    uid = uid,
                    watermarked = watermarked,
                    timestamp = timestamp,
                    lat = locFixForUpload?.lat,
                    lng = locFixForUpload?.lng,
                    studentName = studentName,
                    studentClass = studentClass,
                )

                // 6. Save presensi record to Firestore (larkam_records if payload exists)
                if (larkamPayload != null) {
                    // Save to larkam_records with Larkam stats
                    val payload = mutableMapOf<String, Any?>(
                        "userId" to uid,
                        "distanceKm" to (larkamPayload.distanceKm ?: 0f),
                        "durationSeconds" to (larkamPayload.durationSeconds ?: 0L),
                        "durationFormatted" to (larkamPayload.durationFormatted ?: ""),
                        "routeUrl" to downloadUrl,
                        "imageUrl" to downloadUrl,
                        "timestamp" to timestamp.toString(),
                        "createdAt" to FieldValue.serverTimestamp(),
                    )
                    val locFix = locationFixResult.getOrNull()
                    if (locFix != null) {
                        payload["lat"] = locFix.lat
                        payload["lng"] = locFix.lng
                    }
                    FirebaseFirestore.getInstance().collection("larkam_records")
                        .add(payload).await()
                    LarkamPayloadHolder.clear()
                } else {
                    val locFix = locationFixResult.getOrNull()
                    val saveResult = presensiRepository.savePresensi(
                        userId = uid,
                        timestamp = timestamp,
                        imageUrl = downloadUrl,
                        lat = locFix?.lat,
                        lng = locFix?.lng,
                        studentName = studentName,
                        studentClass = studentClass
                    )
                    saveResult.getOrElse { throw it }
                }

                _uiState.value = PresensiCameraUiState.Success(downloadUrl)
            } catch (e: QueuedOfflineException) {
                _uiState.value = PresensiCameraUiState.QueuedOffline
            } catch (e: Exception) {
                _uiState.value = PresensiCameraUiState.Error(e.message ?: "Gagal memproses presensi")
            } finally {
                try {
                    imageProxy.close()
                } catch (_: Exception) {
                }
                // Do not recycle watermarked here; UI may still display it via Success state if needed.
            }
        }
    }

    /**
     * Test-friendly overload that bypasses ImageProxy/YUV decoding.
     * Directly processes a [bitmap] through watermark -> upload -> firestore.
     * Useful for unit tests without Robolectric ImageProxy mocking.
     */
    fun processBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.value = PresensiCameraUiState.Loading
            try {
                val uid = authRepository.currentUid
                    ?: throw IllegalStateException("User not logged in")
                val userResult = authRepository.getUserProfile(uid)
                val user = userResult.getOrElse { throw it }
                val studentName = user.nama.ifBlank { "Siswa" }
                val studentClass = user.kelas.ifBlank { "-" }

                val locationFixResult = locationProvider.currentLocation()
                if (locationFixResult.isFailure) {
                    throw IllegalStateException("Lokasi tidak tersedia")
                }
                val fix2 = locationFixResult.getOrNull()!!
                if (!isWithinSchoolRadius(fix2.lat, fix2.lng)) {
                    val jarak = calculateDistanceToSchool(fix2.lat, fix2.lng)
                    throw IllegalStateException("Di luar area sekolah! Jarak Anda: ${jarak.toInt()} meter dari sekolah.")
                }
                val location: Location? = Location("fused").apply {
                    latitude = fix2.lat
                    longitude = fix2.lng
                }

                val timestamp = timeProvider.now()
                val larkamPayload = if (LarkamPayloadHolder.hasPayload()) LarkamPayloadHolder else null
                val watermarked = if (larkamPayload != null) {
                    addLarkamWatermark(
                        bitmap = bitmap,
                        studentName = studentName,
                        studentClass = studentClass,
                        location = location,
                        timestamp = timestamp,
                        totalDistanceKm = larkamPayload.distanceKm ?: 0f,
                        durationFormatted = larkamPayload.durationFormatted ?: "--:--"
                    )
                } else {
                    addPresensiWatermark(bitmap, studentName, studentClass, location, timestamp)
                }

                val locFixForUpload = locationFixResult.getOrNull()
                val downloadUrl = uploadSelfie(
                    uid = uid,
                    watermarked = watermarked,
                    timestamp = timestamp,
                    lat = locFixForUpload?.lat,
                    lng = locFixForUpload?.lng,
                    studentName = studentName,
                    studentClass = studentClass,
                )

                if (larkamPayload != null) {
                    val payload = mutableMapOf<String, Any?>(
                        "userId" to uid,
                        "distanceKm" to (larkamPayload.distanceKm ?: 0f),
                        "durationSeconds" to (larkamPayload.durationSeconds ?: 0L),
                        "durationFormatted" to (larkamPayload.durationFormatted ?: ""),
                        "routeUrl" to downloadUrl,
                        "imageUrl" to downloadUrl,
                        "timestamp" to timestamp.toString(),
                        "createdAt" to FieldValue.serverTimestamp(),
                    )
                    val locFix = locationFixResult.getOrNull()
                    if (locFix != null) {
                        payload["lat"] = locFix.lat
                        payload["lng"] = locFix.lng
                    }
                    FirebaseFirestore.getInstance().collection("larkam_records")
                        .add(payload).await()
                    LarkamPayloadHolder.clear()
                } else {
                    val locFix = locationFixResult.getOrNull()
                    val saveResult = presensiRepository.savePresensi(
                        userId = uid,
                        timestamp = timestamp,
                        imageUrl = downloadUrl,
                        lat = locFix?.lat,
                        lng = locFix?.lng,
                        studentName = studentName,
                        studentClass = studentClass
                    )
                    saveResult.getOrElse { throw it }
                }

                _uiState.value = PresensiCameraUiState.Success(downloadUrl)
            } catch (e: QueuedOfflineException) {
                _uiState.value = PresensiCameraUiState.QueuedOffline
            } catch (e: Exception) {
                _uiState.value = PresensiCameraUiState.Error(e.message ?: "Gagal memproses presensi")
            }
        }
    }

    /**
     * Upload step shared by both entries. Legacy path uploads directly; when
     * [offlineRepository] is wired, a dead network (or network failure)
     * persists the capture to the Room queue + schedules the sync worker and
     * signals via [QueuedOfflineException] instead of returning a URL.
     */
    private suspend fun uploadSelfie(
        uid: String,
        watermarked: Bitmap,
        timestamp: LocalDateTime,
        lat: Double?,
        lng: Double?,
        studentName: String,
        studentClass: String,
    ): String {
        val offline = offlineRepository
        if (offline == null) {
            return storageRepository.uploadPresensiSelfie(uid, watermarked, timestamp).getOrThrow()
        }
        val bytes = withContext(Dispatchers.Default) { watermarked.toCompressJpegByteArray(quality = 80) }
        return when (
            val result = offline.uploadOrQueue(
                userId = uid,
                timestamp = timestamp,
                imageBytes = bytes,
                lat = lat,
                lng = lng,
                studentName = studentName,
                studentClass = studentClass,
            ).getOrThrow()
        ) {
            is PresensiUploadResult.Uploaded -> result.imageUrl
            PresensiUploadResult.QueuedOffline -> throw QueuedOfflineException()
        }
    }

    fun reset() {
        _uiState.value = PresensiCameraUiState.Idle
    }

    companion object {
        fun factory(
            storageRepository: StorageRepository = CloudinaryStorageRepository(),
            presensiRepository: PresensiRepository = FirebasePresensiRepository(),
            locationProvider: LocationProvider? = null,
            authRepository: AuthRepository = FirebaseAuthRepository(),
            timeProvider: TimeProvider = SystemTimeProvider(),
            offlineRepository: OfflinePresensiRepository? = null,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                // LocationProvider needs Context; if not supplied, create a no-op that fails gracefully.
                // In production, AppNavHost should supply FusedLocationProvider.
                val locProvider = locationProvider ?: object : LocationProvider {
                    override suspend fun currentLocation(): Result<com.gynda.fridaystm.util.LocationFix> =
                        Result.failure(IllegalStateException("LocationProvider not initialized"))
                }
                PresensiCameraViewModel(
                    storageRepository = storageRepository,
                    presensiRepository = presensiRepository,
                    locationProvider = locProvider,
                    authRepository = authRepository,
                    timeProvider = timeProvider,
                    offlineRepository = offlineRepository,
                )
            }
        }
    }
}

sealed interface PresensiCameraUiState {
    data object Idle : PresensiCameraUiState
    data object Loading : PresensiCameraUiState
    data class Success(val downloadUrl: String) : PresensiCameraUiState
    data class Error(val message: String) : PresensiCameraUiState

    /**
     * Capture persisted to the offline queue (Room + scheduled sync worker).
     * The UI treats it like a soft success: the Dashboard banner counts it
     * until the worker drains it.
     */
    data object QueuedOffline : PresensiCameraUiState
}

/** Control-flow signal: the capture was queued, not uploaded — maps to [PresensiCameraUiState.QueuedOffline]. */
private class QueuedOfflineException : Exception()

/**
 * Converts [ImageProxy] to [Bitmap]. Tries JPEG decode first; falls back to a
 * placeholder for YUV / test environments where full YUV->RGB conversion is not
 * needed (unit tests inject [imageProxyConverter] anyway).
 */
private fun ImageProxy.toBitmapCompat(): Bitmap {
    return try {
        // Common path for ImageCapture with JPEG format
        val buffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalStateException("decode returned null")
    } catch (_: Exception) {
        // Fallback dummy for YUV_420_888 or test fakes
        Bitmap.createBitmap(
            if (width > 0) width else 800,
            if (height > 0) height else 600,
            Bitmap.Config.ARGB_8888
        ).apply { eraseColor(android.graphics.Color.GRAY) }
    }
}
