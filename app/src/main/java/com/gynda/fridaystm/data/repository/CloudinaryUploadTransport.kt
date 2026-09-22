package com.gynda.fridaystm.data.repository

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

internal suspend fun <T> cloudinaryResult(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(error)
}

/** Cancel the real HTTP call when its coroutine is cancelled; always close responses. */
internal suspend fun uploadCloudinary(client: OkHttpClient, request: Request, cloudName: String): String =
    suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        client.dispatcher.executorService.execute {
            val outcome = runCatching {
                call.execute().use { response ->
                    requireSuccessfulCloudinaryResponse(response.code)
                    val payload = response.body?.string().orEmpty()
                    verifiedCloudinaryUrl(JSONObject(payload).optString("secure_url"), cloudName)
                }
            }
            continuation.resumeWith(outcome)
        }
    }


internal fun requireSuccessfulCloudinaryResponse(code: Int) {
    if (code in 200..299) return
    val message = "Cloudinary upload failed (HTTP $code)"
    if (code == 408 || code == 429 || code >= 500) throw IOException(message)
    throw IllegalStateException(message)
}

/** Only HTTPS media delivered from the configured Cloudinary cloud is evidence. */
internal fun verifiedCloudinaryUrl(value: String, cloudName: String): String {
    val url = value.toHttpUrlOrNull()
    require(url != null && url.isHttps && url.host == "res.cloudinary.com" && url.port == 443 &&
        url.username.isEmpty() && url.password.isEmpty() &&
        url.pathSegments.take(3) == listOf(cloudName, "image", "upload") &&
        url.pathSegments.size > 3 && url.pathSegments.last().isNotBlank()) {
        "Cloudinary returned an invalid media URL"
    }
    return value
}
