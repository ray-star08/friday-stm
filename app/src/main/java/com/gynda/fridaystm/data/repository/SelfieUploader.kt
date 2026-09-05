package com.gynda.fridaystm.data.repository

import android.graphics.Bitmap
import com.gynda.fridaystm.BuildConfig
import com.gynda.fridaystm.util.CloudinaryConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * Uploads selfie evidence to Cloudinary via an **unsigned upload preset** (M4.3,
 * SKILL policy — Cloudinary, not Firebase Storage). Assets land under
 * `selfies/{uid}/` with the deterministic public id `{date}_{phase}`.
 *
 * An interface (SKILL.md §9) so the camera flow is testable with a fake. The
 * upload is a `suspend` returning a [Result] — no callback listener (SKILL.md §5).
 * JPEG compression + the blocking HTTP call run on `Dispatchers.IO`.
 */
interface SelfieUploader {

    /**
     * Compresses [bitmap] to JPEG and uploads it, returning the Cloudinary
     * `secure_url` to store on the attendance document.
     *
     * @param phase one of [com.gynda.fridaystm.util.SelfiePhase] (`apel` | `pembiasaan`).
     * @return the `secure_url` string, or [Result.failure] on any I/O / API error.
     */
    suspend fun uploadSelfie(uid: String, date: String, phase: String, bitmap: Bitmap): Result<String>
}

/**
 * Cloudinary-backed [SelfieUploader] using OkHttp multipart.
 *
 * `cloudName` / `uploadPreset` are read from [BuildConfig] (injected from
 * `local.properties` at build time) so no keys are hard-coded in source
 * (SKILL.md §8). Unsigned uploads carry no API secret — the preset restricts
 * what clients may do server-side.
 */
class CloudinaryUploader(
    private val client: OkHttpClient = OkHttpClient(),
    private val cloudName: String = BuildConfig.CLOUDINARY_CLOUD_NAME,
    private val uploadPreset: String = BuildConfig.CLOUDINARY_UPLOAD_PRESET,
) : SelfieUploader {

    override suspend fun uploadSelfie(
        uid: String,
        date: String,
        phase: String,
        bitmap: Bitmap,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(cloudName.isNotBlank() && uploadPreset.isNotBlank()) {
                "Cloudinary not configured: set cloudinary.cloudName / cloudinary.uploadPreset in local.properties"
            }

            val bytes = ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
                stream.toByteArray()
            }

            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("upload_preset", uploadPreset)
                .addFormDataPart("folder", CloudinaryConfig.folder(uid))
                .addFormDataPart("public_id", CloudinaryConfig.publicId(date, phase))
                .addFormDataPart(
                    "file",
                    "$phase.jpg",
                    bytes.toRequestBody(JPEG_MIME.toMediaType()),
                )
                .build()

            val request = Request.Builder()
                .url(CloudinaryConfig.uploadUrl(cloudName))
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("Cloudinary upload failed (${response.code}): $payload")
                }
                JSONObject(payload).optString("secure_url").ifBlank {
                    error("Cloudinary response missing secure_url")
                }
            }
        }
    }

    private companion object {
        /** ~70% keeps faces legible while cutting upload size (task 4.3 compress). */
        const val JPEG_QUALITY = 70
        const val JPEG_MIME = "image/jpeg"
    }
}
