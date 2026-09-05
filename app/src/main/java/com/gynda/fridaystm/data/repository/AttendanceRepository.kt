package com.gynda.fridaystm.data.repository

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.gynda.fridaystm.data.model.AttendanceRecord
import com.gynda.fridaystm.data.model.CheckoutStamp
import com.gynda.fridaystm.data.model.PembiasaanStamp
import com.gynda.fridaystm.util.AttendanceFields
import com.gynda.fridaystm.util.FirestoreCollections
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Reads and writes the per-day attendance document `attendance/{uid}_{date}`.
 *
 * An interface (SKILL.md §9) so `HomeViewModel` can be tested with a fake. The
 * live [observeTodayRecord] stream is the *only* sanctioned snapshot listener,
 * wrapped in `callbackFlow`; every write is a `suspend` + `.await()` returning a
 * [Result] (SKILL.md §5). `DocumentSnapshot`/`Task` never escape this layer.
 */
interface AttendanceRepository {

    /**
     * Streams today's record, or `null` while the document does not exist yet.
     * Re-emits on every server/local change.
     *
     * @param date ISO `yyyy-MM-dd`; combined with [uid] into the deterministic id.
     */
    fun observeTodayRecord(uid: String, date: String): Flow<AttendanceRecord?>

    /**
     * Streams this user's full attendance history, most-recent day first.
     *
     * Ordered by `date` descending — the ISO `yyyy-MM-dd` string sorts
     * chronologically, so no separate timestamp field is needed (M5.1).
     */
    fun observeHistory(uid: String): Flow<List<AttendanceRecord>>

    /** Writes the Fase 2 (Pembiasaan) stamp, merging into the day's document. */
    suspend fun submitPembiasaan(uid: String, date: String, grade: Int, stamp: PembiasaanStamp): Result<Unit>

    /** Writes the Fase 3 (Check-out) stamp and marks the day complete. */
    suspend fun submitCheckout(uid: String, date: String, stamp: CheckoutStamp): Result<Unit>
}

/**
 * Firestore-backed [AttendanceRepository].
 *
 * Writes use `SetOptions.merge()` with a **field map** (not the full POJO) so a
 * single-phase submit only touches its own key and never nulls out the sibling
 * phases. The deterministic `uid_date` id (see [AttendanceRecord.docIdFor]) makes
 * duplicate check-ins structurally impossible.
 *
 * **Time is server-authored.** Every write stamps
 * [AttendanceFields.UPDATED_AT] with [FieldValue.serverTimestamp], and the
 * per-phase `serverTime` fields are `@ServerTimestamp`-annotated and left `null`
 * by the caller, so Firestore fills both from its own clock. The rules assert
 * `request.resource.data.updatedAt == request.time`, so a device with a tampered
 * clock cannot forge an attendance time — it is rejected server-side.
 * The `time` (`"HH:mm"`) field on each stamp is device-formatted **display text
 * only** and is never treated as evidence.
 */
class FirestoreAttendanceRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : AttendanceRepository {

    private fun docRef(uid: String, date: String): DocumentReference =
        firestore.collection(FirestoreCollections.ATTENDANCE)
            .document(AttendanceRecord.docIdFor(uid, date))

    override fun observeTodayRecord(uid: String, date: String): Flow<AttendanceRecord?> = callbackFlow {
        val registration = docRef(uid, date).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val record = snapshot
                ?.takeIf { it.exists() }
                ?.toObject(AttendanceRecord::class.java)
            trySend(record)
        }
        awaitClose { registration.remove() }
    }

    override fun observeHistory(uid: String): Flow<List<AttendanceRecord>> = callbackFlow {
        val registration = firestore.collection(FirestoreCollections.ATTENDANCE)
            .whereEqualTo(AttendanceFields.UID, uid)
            .orderBy(AttendanceFields.DATE, Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObjects(AttendanceRecord::class.java).orEmpty())
            }
        awaitClose { registration.remove() }
    }

    override suspend fun submitPembiasaan(
        uid: String,
        date: String,
        grade: Int,
        stamp: PembiasaanStamp,
    ): Result<Unit> = runCatching {
        docRef(uid, date).set(
            pembiasaanMergePayload(uid, date, grade, stamp),
            SetOptions.merge(),
        ).await()
        Unit
    }

    override suspend fun submitCheckout(
        uid: String,
        date: String,
        stamp: CheckoutStamp,
    ): Result<Unit> = runCatching {
        docRef(uid, date).set(
            checkoutMergePayload(uid, date, stamp),
            SetOptions.merge(),
        ).await()
        Unit
    }
}
