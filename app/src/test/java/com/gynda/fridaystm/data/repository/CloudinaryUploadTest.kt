package com.gynda.fridaystm.data.repository

import com.gynda.fridaystm.util.TimeProvider
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CloudinaryUploadTest {
    private fun client(code: Int, body: String) = OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
            .code(code).message("fixture").body(body.toResponseBody()).build()
    }.build()

    @Test fun `evidence ids do not collide for different photos or same-time permits`() = runBlocking {
        val ids = mutableListOf<String>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val body = chain.request().body as okhttp3.MultipartBody
            val part = body.parts.single { it.headers?.get("Content-Disposition")?.contains("name=\"public_id\"") == true }
            val buffer = okio.Buffer()
            part.body.writeTo(buffer)
            synchronized(ids) { ids.add(buffer.readUtf8()) }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body("{\"secure_url\":\"https://res.cloudinary.com/fixture/image/upload/a.jpg\"}".toResponseBody()).build()
        }.build()
        val selfie = CloudinaryUploader(http, "fixture", "preset")
        for (color in listOf(android.graphics.Color.RED, android.graphics.Color.BLUE)) {
            val bitmap = android.graphics.Bitmap.createBitmap(8, 8, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
            assertTrue(selfie.uploadSelfie("alice", "2026-09-18", "pembiasaan", bitmap).isSuccess)
        }
        assertNotEquals("retake must not reuse old evidence", ids[0], ids[1])
        val time = object : TimeProvider {
            override fun now() = LocalDateTime.of(2026, 9, 18, 7, 0)
            override fun weekOfYear() = 38
        }
        val permit = CloudinaryIzinProofUploader(http, "fixture", "preset", time)
        repeat(2) { assertTrue(permit.uploadProof("alice", byteArrayOf(it.toByte())).isSuccess) }
        assertNotEquals("two same-time permit photos must not collide", ids[2], ids[3])
    }

    @Test fun `all upload entry points propagate cancellation and reject foreign media`() = runBlocking {
        val time = object : TimeProvider {
            override fun now() = LocalDateTime.of(2026, 9, 18, 7, 0)
            override fun weekOfYear() = 38
        }
        suspend fun upload(kind: Int, http: OkHttpClient): Result<String> = when (kind) {
            0 -> CloudinaryStorageRepository(http, "fixture", "preset")
                .uploadPresensiBytes("alice", byteArrayOf(1), "capture.jpg")
            1 -> CloudinaryUploader(http, "fixture", "preset").uploadSelfie("alice", "2026-09-18", "pembiasaan",
                android.graphics.Bitmap.createBitmap(8, 8, android.graphics.Bitmap.Config.ARGB_8888))
            else -> CloudinaryIzinProofUploader(http, "fixture", "preset", time).uploadProof("alice", byteArrayOf(1))
        }
        for (kind in 0..2) {
            assertTrue("entry $kind", upload(kind, client(200, "{\"secure_url\":\"https://attacker.invalid/a.jpg\"}")).isFailure)
            val cancelled = OkHttpClient.Builder().addInterceptor { throw kotlinx.coroutines.CancellationException("fixture cancel") }.build()
            var propagated = false
            try { upload(kind, cancelled) } catch (_: kotlinx.coroutines.CancellationException) { propagated = true }
            assertTrue("entry $kind must propagate cancellation", propagated)
        }
    }

    @Test fun `transient Cloudinary failures remain retryable network errors`() = runBlocking {
        for (code in listOf(408, 429, 500, 503)) {
            val repo = CloudinaryStorageRepository(client(code, "{\"error\":\"sensitive fixture\"}"), "fixture", "preset")
            val error = repo.uploadPresensiBytes("alice", byteArrayOf(1), "capture.jpg").exceptionOrNull()
            assertTrue("HTTP $code should be retryable", error is IOException)
            assertFalse(error?.message.orEmpty().contains("sensitive fixture"))
        }
    }

    @Test fun `foreign or insecure secure_url is not accepted as evidence`() = runBlocking {
        for (url in listOf("http://res.cloudinary.com/fixture/image/upload/a.jpg", "https://attacker.invalid/a.jpg", "https://res.cloudinary.com/other-cloud/image/upload/a.jpg")) {
            val repo = CloudinaryStorageRepository(client(200, "{\"secure_url\":\"$url\"}"), "fixture", "preset")
            assertTrue(url, repo.uploadPresensiBytes("alice", byteArrayOf(1), "capture.jpg").isFailure)
        }
    }
}
