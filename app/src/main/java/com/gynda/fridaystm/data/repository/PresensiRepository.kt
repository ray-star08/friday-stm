package com.gynda.fridaystm.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.gynda.fridaystm.data.model.PresensiRecord
import com.gynda.fridaystm.util.FirestoreCollections
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Persists presensi records after watermark + Storage upload.
 * Collection: `presensi_records` (also supports `larkam_runs` alias via same payload).
 */
interface PresensiRepository {
    suspend fun savePresensi(
        userId: String,
        timestamp: LocalDateTime,
        imageUrl: String,
        lat: Double?,
        lng: Double?,
        studentName: String,
        studentClass: String,
    ): Result<Unit>

    /**
     * Streams history for [userId] ordered by timestamp descending.
     * Emits [Result.success] with list or [Result.failure] on error.
     */
    fun getPresensiHistory(userId: String): Flow<Result<List<PresensiRecord>>>
}

class FirebasePresensiRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : PresensiRepository {

    private val isoFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    override suspend fun savePresensi(
        userId: String,
        timestamp: LocalDateTime,
        imageUrl: String,
        lat: Double?,
        lng: Double?,
        studentName: String,
        studentClass: String,
    ): Result<Unit> = runCatching {
        val payload = mutableMapOf<String, Any?>(
            "userId" to userId,
            "timestamp" to timestamp.format(isoFormatter),
            "imageUrl" to imageUrl,
            "studentName" to studentName,
            "studentClass" to studentClass,
            "createdAt" to FieldValue.serverTimestamp(),
        )
        if (lat != null && lng != null) {
            payload["lat"] = lat
            payload["lng"] = lng
        }
        firestore.collection(FirestoreCollections.PRESENSI_RECORDS)
            .add(payload)
            .await()
        Unit
    }

    override fun getPresensiHistory(userId: String): Flow<Result<List<PresensiRecord>>> = callbackFlow {
        val query: Query = firestore.collection(FirestoreCollections.PRESENSI_RECORDS)
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Result.failure(error))
                return@addSnapshotListener
            }
            val list = snapshot?.toObjects(PresensiRecord::class.java).orEmpty()
            trySend(Result.success(list))
        }
        awaitClose { registration.remove() }
    }
}
