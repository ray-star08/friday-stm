package com.gynda.fridaystm.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.gynda.fridaystm.data.model.SenamSession
import com.gynda.fridaystm.util.FirestoreCollections
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Reads/writes the weekly Senam video document `senam_sessions/{weekId}`.
 *
 * Instructors [setSession] the week's video; everyone else [observeSession]s it to
 * follow the routine on Home. An interface (SKILL.md §9) so ViewModels test with a
 * fake; the live read is the only sanctioned snapshot listener (`callbackFlow`,
 * SKILL.md §5) and the write is a `suspend` + `.await()` returning a [Result].
 *
 * Role enforcement is the caller's + Firestore rules' job — the rules must reject
 * writes from non-instructors (a client check alone is insufficient, SKILL.md §8).
 */
interface SenamRepository {

    /** Streams the Senam session for [weekId], or `null` until an instructor sets one. */
    fun observeSession(weekId: String): Flow<SenamSession?>

    /** Upserts the week's Senam video (instructor/admin only). */
    suspend fun setSession(session: SenamSession): Result<Unit>
}

/** Firestore-backed [SenamRepository]. */
class FirestoreSenamRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : SenamRepository {

    override fun observeSession(weekId: String): Flow<SenamSession?> = callbackFlow {
        val registration = firestore.collection(FirestoreCollections.SENAM_SESSIONS)
            .document(weekId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(
                    snapshot?.takeIf { it.exists() }?.toObject(SenamSession::class.java),
                )
            }
        awaitClose { registration.remove() }
    }

    override suspend fun setSession(session: SenamSession): Result<Unit> = runCatching {
        firestore.collection(FirestoreCollections.SENAM_SESSIONS)
            .document(session.weekId)
            .set(session, SetOptions.merge())
            .await()
        Unit
    }
}
