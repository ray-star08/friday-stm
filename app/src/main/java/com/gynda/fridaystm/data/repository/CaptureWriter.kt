package com.gynda.fridaystm.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.gynda.fridaystm.data.model.CaptureDraft
import com.gynda.fridaystm.util.FirestoreCollections
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Confirms append-only supplementary evidence, never canonical attendance. */
fun interface CaptureWriter {
    suspend fun saveCapture(draft: CaptureDraft, imageUrl: String): Result<Unit>
}

/** Server-only get/create seam; cached reads must never confirm a write. */
interface CaptureDocumentSource {
    suspend fun get(collection: String, documentId: String): Map<String, Any?>?
    suspend fun create(collection: String, documentId: String, payload: Map<String, Any?>)
}

internal fun confirmedCaptureData(data: Map<String, Any?>?, fromCache: Boolean, pendingWrites: Boolean): Map<String, Any?>? {
    if (fromCache || pendingWrites) throw java.io.IOException("Capture is not yet confirmed by the server")
    return data
}

/** Requires append-only server rules and missing-document GET permission for capture UUIDs. */
class FirebaseCaptureDocumentSource(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : CaptureDocumentSource {
    override suspend fun get(collection: String, documentId: String): Map<String, Any?>? {
        val snapshot = firestore.collection(collection).document(documentId).get(Source.SERVER).await()
        return confirmedCaptureData(snapshot.data, snapshot.metadata.isFromCache, snapshot.metadata.hasPendingWrites())
    }

    override suspend fun create(collection: String, documentId: String, payload: Map<String, Any?>) {
        // Android has no create-only DocumentReference operation. Server rules reject updates.
        firestore.collection(collection).document(documentId)
            .set(payload + ("createdAt" to FieldValue.serverTimestamp())).await()
    }
}

class CaptureIdentityConflict : IllegalStateException("Capture ID already belongs to different evidence")

class IdempotentCaptureWriter(
    private val source: CaptureDocumentSource,
    private val operationTimeoutMillis: Long = 20_000,
    private val currentUid: () -> String?,
) : CaptureWriter {
    override suspend fun saveCapture(draft: CaptureDraft, imageUrl: String): Result<Unit> {
        val result = kotlinx.coroutines.withTimeoutOrNull(operationTimeoutMillis) { saveWithinDeadline(draft, imageUrl) }
            ?: Result.failure(java.io.IOException("Capture acknowledgement timed out; retained for retry"))
        // A failed get/readback has no post-await success path. Recheck owner
        // before classifying that failure, just as for a normally returned read.
        return if (result.isFailure && currentUid() != draft.userId) Result.failure(CaptureOwnerChanged()) else result
    }

    private suspend fun saveWithinDeadline(draft: CaptureDraft, imageUrl: String): Result<Unit> = captureResult {
        val payload = capturePayload(draft, imageUrl)
        val collection = if (draft.larkam == null) FirestoreCollections.PRESENSI_RECORDS else FirestoreCollections.LARKAM_RECORDS
        val documentId = "capture_${draft.captureId}"
        requireOwner(draft.userId)
        val existing = source.get(collection, documentId)
        requireOwner(draft.userId)
        if (existing != null) {
            requireIdentical(existing, payload)
        } else {
            try {
                source.create(collection, documentId, payload)
                requireOwner(draft.userId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                requireOwner(draft.userId)
                val confirmed = try {
                    source.get(collection, documentId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    throw failure
                }
                requireOwner(draft.userId)
                if (confirmed == null) throw failure
                requireIdentical(confirmed, payload)
            }
        }
    }

    private fun requireOwner(owner: String) {
        if (currentUid() != owner) throw CaptureOwnerChanged()
    }

    private fun requireIdentical(actual: Map<String, Any?>, expected: Map<String, Any?>) {
        if (!sameValue(actual - "createdAt", expected)) throw CaptureIdentityConflict()
    }

    // Firestore deserializes integral numbers as Long; compare their numeric value,
    // while preserving exact field presence, strings, route order, and all business fields.
    private fun sameValue(actual: Any?, expected: Any?): Boolean = when {
        actual is Number && expected is Number -> actual.toString().toBigDecimalOrNull() == expected.toString().toBigDecimalOrNull()
        actual is Map<*, *> && expected is Map<*, *> -> actual.keys == expected.keys && actual.keys.all { sameValue(actual[it], expected[it]) }
        actual is List<*> && expected is List<*> -> actual.size == expected.size && actual.indices.all { sameValue(actual[it], expected[it]) }
        else -> actual == expected
    }
}

/** Production writer is lazy so offline queueing never initializes Firebase unnecessarily. */
class FirebaseCaptureWriter : CaptureWriter {
    private val delegate by lazy {
        IdempotentCaptureWriter(FirebaseCaptureDocumentSource()) { FirebaseAuth.getInstance().currentUser?.uid }
    }
    override suspend fun saveCapture(draft: CaptureDraft, imageUrl: String) = delegate.saveCapture(draft, imageUrl)
}

internal fun capturePayload(draft: CaptureDraft, imageUrl: String): Map<String, Any?> = buildMap {
    put("schemaVersion", 2)
    put("captureId", draft.captureId)
    put("userId", draft.userId)
    put("timestamp", draft.timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
    put("imageUrl", imageUrl)
    put("studentName", draft.studentName)
    put("studentClass", draft.studentClass)
    if (draft.lat != null && draft.lng != null) {
        put("lat", draft.lat)
        put("lng", draft.lng)
    }
    draft.larkam?.let { run ->
        put("distanceKm", run.distanceKm)
        put("durationSeconds", run.durationSeconds)
        put("durationFormatted", String.format(Locale.ROOT, "%02d:%02d", run.durationSeconds / 60, run.durationSeconds % 60))
        put("route", run.route.map { it.toMap() })
        put("routeUrl", imageUrl)
    }
}

internal suspend inline fun <T> captureResult(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Exception) {
    Result.failure(failure)
}
