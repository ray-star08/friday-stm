package com.gynda.fridaystm.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.gynda.fridaystm.data.model.TalimSummary
import com.gynda.fridaystm.util.FirestoreCollections
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Reads/writes the per-class Ta'lim summary `talim_summaries/{date}_{kelas}`.
 *
 * Only the class representative (`class_rep`) submits; the deterministic id makes
 * duplicate submissions structurally impossible. An interface (SKILL.md §9) so the
 * ViewModel tests with a fake; the write is a `suspend` + `.await()` → [Result]
 * (SKILL.md §5). Role enforcement lives in Firestore rules, not just the client.
 */
interface TalimRepository {

    /** Streams the summary for a class on a date, or `null` until submitted. */
    fun observeSummary(date: String, kelas: String): Flow<TalimSummary?>

    /** Writes the class summary (class-rep/admin only). Overwrites by deterministic id. */
    suspend fun submitSummary(summary: TalimSummary): Result<Unit>
}

/** Firestore-backed [TalimRepository]. */
class FirestoreTalimRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : TalimRepository {

    override fun observeSummary(date: String, kelas: String): Flow<TalimSummary?> = callbackFlow {
        val registration = firestore.collection(FirestoreCollections.TALIM_SUMMARIES)
            .document(TalimSummary.docIdFor(date, kelas))
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(
                    snapshot?.takeIf { it.exists() }?.toObject(TalimSummary::class.java),
                )
            }
        awaitClose { registration.remove() }
    }

    override suspend fun submitSummary(summary: TalimSummary): Result<Unit> = runCatching {
        firestore.collection(FirestoreCollections.TALIM_SUMMARIES)
            .document(summary.docId)
            .set(summary)
            .await()
        Unit
    }
}
