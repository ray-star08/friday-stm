package com.gynda.fridaystm.data.repository

import com.gynda.fridaystm.data.model.CaptureDraft
import com.gynda.fridaystm.data.model.LarkamCapture
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.LocalDateTime

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CaptureWriterTest {
    private val draft = CaptureDraft(
        "b0802f1b-c6da-4ca9-86c9-82f833b92222", "owner", LocalDateTime.of(2026, 9, 25, 7, 30),
        -6.88, 107.53, "Synthetic", "XI TEST", LarkamCapture(2.75, 901, listOf(mapOf("lat" to -6.88, "lng" to 107.53))),
    )
    private val url = "https://example.invalid/capture.jpg"
    private class Source : CaptureDocumentSource {
        val docs = mutableMapOf<String, Map<String, Any?>>()
        var writes = 0
        var failAfterWrite = false
        override suspend fun get(collection: String, documentId: String) = docs["$collection/$documentId"]
        override suspend fun create(collection: String, documentId: String, payload: Map<String, Any?>) {
            writes++
            check(docs.putIfAbsent("$collection/$documentId", payload) == null) { "append only" }
            if (failAfterWrite) throw IOException("response lost after accepted commit")
        }
    }
    @Test fun unacknowledgedWriteTimesOutAsRetryableInsteadOfLockingEveryCapture() = runTest {
        val source = object : CaptureDocumentSource {
            override suspend fun get(collection: String, documentId: String): Map<String, Any?>? = null
            override suspend fun create(collection: String, documentId: String, payload: Map<String, Any?>) {
                kotlinx.coroutines.delay(500)
                throw IOException("late unavailable")
            }
        }
        val writer = IdempotentCaptureWriter(source, operationTimeoutMillis = 100) { "owner" }
        assertTrue(writer.saveCapture(draft, url).exceptionOrNull() is IOException)
        assertTrue("must release the queue before the late transport failure", testScheduler.currentTime <= 100)
    }

    @Test fun existingConflictingEvidenceNeverOverwrites() = runTest {
        val source = Source()
        val writer = IdempotentCaptureWriter(source) { "owner" }
        writer.saveCapture(draft, url).getOrThrow()
        assertTrue(writer.saveCapture(draft.copy(studentName = "different"), url).exceptionOrNull() is CaptureIdentityConflict)
        assertEquals(1, source.writes)
    }

    @Test fun pendingOrCachedSnapshotsNeverConfirmServerPersistence() {
        for ((cached, pending) in listOf(true to false, false to true, true to true)) {
            val result = runCatching { confirmedCaptureData(mapOf("userId" to "owner"), cached, pending) }
            assertTrue("uncommitted read must remain retryable", result.exceptionOrNull() is IOException)
        }
        assertEquals(mapOf("userId" to "owner"), confirmedCaptureData(mapOf("userId" to "owner"), false, false))
        assertNull(confirmedCaptureData(null, false, false))
    }

    @Test fun uncertainCommitIsConfirmedByExactReadbackAndDuplicateDoesNotWrite() = runTest {
        val source = Source().apply { failAfterWrite = true }
        val writer = IdempotentCaptureWriter(source) { "owner" }
        assertTrue("accepted write must be confirmed after a lost response", writer.saveCapture(draft, url).isSuccess)
        assertTrue(writer.saveCapture(draft, url).isSuccess)
        assertEquals(1, source.writes)
        val payload = source.docs.getValue("larkam_records/capture_${draft.captureId}")
        assertEquals(2, payload["schemaVersion"])
        assertEquals(draft.captureId, payload["captureId"])
        assertEquals(draft.timestamp.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME), payload["timestamp"])
        assertEquals(2.75, payload["distanceKm"])
        assertEquals(901L, payload["durationSeconds"])
        assertEquals("15:01", payload["durationFormatted"])
        assertEquals(draft.larkam!!.route, payload["route"])
        assertEquals(url, payload["imageUrl"])
        assertEquals(url, payload["routeUrl"])
    }
}
