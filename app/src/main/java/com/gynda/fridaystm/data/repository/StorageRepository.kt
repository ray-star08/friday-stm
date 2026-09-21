package com.gynda.fridaystm.data.repository

import android.graphics.Bitmap
import com.google.firebase.storage.FirebaseStorage
import com.gynda.fridaystm.BuildConfig
import com.gynda.fridaystm.util.CloudinaryConfig
import com.gynda.fridaystm.util.toCompressJpegByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Handles Firebase Storage upload for presensi selfies.
 *
 * Path: `presensi_selfies/{userId}/{timestamp}.jpg` where timestamp is
 * `yyyyMMdd_HHmmss` derived from [LocalDateTime]. Bitmap is compressed via
 * [toCompressJpegByteArray] at quality 80 before upload to save bandwidth and
 * avoid OOM.
 */
interface StorageRepository {
    /**
     * Uploads [bitmap] for [userId] at [timestamp].
     * @return [Result.success] with download URL or [Result.failure].
     */
    suspend fun uploadPresensiSelfie(
        userId: String,
        bitmap: Bitmap,
        timestamp: LocalDateTime,
    ): Result<String>

    /**
     * Uploads already-compressed [bytes] (JPEG) to [storageFileName]
     * (relative object path, e.g. `presensi_selfies/{uid}/{ts}.jpg`).
     *
     * Bytes-first so the offline queue and the sync worker — which only ever
     * handle cache files, never `Bitmap`s — stay unit-testable on the JVM.
     *
     * @return [Result.success] with download URL or [Result.failure].
     */
    suspend fun uploadPresensiBytes(
        userId: String,
        bytes: ByteArray,
        storageFileName: String,
    ): Result<String>
}

class FirebaseStorageRepository(
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
) : StorageRepository {

    override suspend fun uploadPresensiSelfie(
        userId: String,
        bitmap: Bitmap,
        timestamp: LocalDateTime,
    ): Result<String> = runCatching {
        // Compress efficiently via WatermarkUtils helper, then share the putBytes path.
        val bytes = bitmap.toCompressJpegByteArray(quality = 80)
        val fileName = "presensi_selfies/$userId/${timestamp.format(fileNameFormatter)}.jpg"
        uploadPresensiBytes(userId, bytes, fileName).getOrThrow()
    }

    override suspend fun uploadPresensiBytes(
        userId: String,
        bytes: ByteArray,
        storageFileName: String,
    ): Result<String> = runCatching {
        require(userId.isNotBlank()) { "userId must not be blank" }
        require(bytes.isNotEmpty()) { "photo bytes must not be empty" }
        require(storageFileName.isNotBlank()) { "storageFileName must not be blank" }

        val ref = storage.reference.child(storageFileName)

        ref.putBytes(bytes).await()
        val url = ref.downloadUrl.await()
        url.toString()
    }

    companion object {
        private val fileNameFormatter: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

        /** Storage object path for a capture — shared by the online path and the offline queue. */
        fun presensiFileName(userId: String, timestamp: LocalDateTime): String =
            "presensi_selfies/$userId/${timestamp.format(fileNameFormatter)}.jpg"
    }
}

/**
 * Cloudinary-backed [StorageRepository] — SKILL.md policy: Cloudinary, not Firebase Storage.
 *
 * Mengunggah bytes presensi ke `presensi/{userId}/{yyyyMMdd_HHmmss}` via unsigned preset.
 * Dipakai oleh [OfflineFirstPresensiRepository] dan [com.gynda.fridaystm.worker.PresensiSyncWorker]
 * agar presensi tidak lagi 404 `StorageException -13010`.
 */
class CloudinaryStorageRepository(
    private val client: OkHttpClient = OkHttpClient(),
    private val cloudName: String = BuildConfig.CLOUDINARY_CLOUD_NAME,
    private val uploadPreset: String = BuildConfig.CLOUDINARY_UPLOAD_PRESET,
) : StorageRepository {

    override suspend fun uploadPresensiSelfie(
        userId: String,
        bitmap: Bitmap,
        timestamp: LocalDateTime,
    ): Result<String> = runCatching {
        val bytes = bitmap.toCompressJpegByteArray(quality = 80)
        val fileName = FirebaseStorageRepository.presensiFileName(userId, timestamp)
        uploadPresensiBytes(userId, bytes, fileName).getOrThrow()
    }

    override suspend fun uploadPresensiBytes(
        userId: String,
        bytes: ByteArray,
        storageFileName: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(userId.isNotBlank()) { "userId must not be blank" }
            require(bytes.isNotEmpty()) { "photo bytes must not be empty" }
            require(storageFileName.isNotBlank()) { "storageFileName must not be blank" }
            require(cloudName.isNotBlank() && uploadPreset.isNotBlank()) {
                "Cloudinary not configured: set cloudinary.cloudName / cloudinary.uploadPreset in local.properties"
            }
            // storageFileName = presensi_selfies/{uid}/{yyyyMMdd_HHmmss}.jpg -> public_id = timestamp
            val baseName = storageFileName.substringAfterLast("/").substringBeforeLast(".")
            val folder = "presensi/$userId"
            val publicId = baseName.ifBlank { storageFileName.substringAfterLast("/").substringBefore(".") }

            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("upload_preset", uploadPreset)
                .addFormDataPart("folder", folder)
                .addFormDataPart("public_id", publicId)
                .addFormDataPart("file", "$publicId.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
                .build()

            val request = Request.Builder()
                .url(CloudinaryConfig.uploadUrl(cloudName))
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("Cloudinary presensi upload failed (${response.code}): $payload")
                }
                JSONObject(payload).optString("secure_url").ifBlank {
                    error("Cloudinary response missing secure_url: $payload")
                }
            }
        }
    }
}
