package com.gynda.fridaystm.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.gynda.fridaystm.data.model.RotationSchedule
import com.gynda.fridaystm.util.FirestoreCollections
import com.gynda.fridaystm.util.FirestoreDocIds
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine

/**
 * Reads the read-only `rotations` reference collection — the **optional** weekly
 * rotation override (task.md 3.2).
 *
 * The rotation itself is a pure formula (`domain/activityForGrade`); this
 * repository only supplies the exception to it, so the school can pin a holiday
 * or a special week without a release. Nothing here is required for the app to
 * work: **absence is the normal case.**
 *
 * An interface (SKILL.md §9) so `HomeViewModel` is testable with a fake; the
 * live stream is `callbackFlow`, the only sanctioned snapshot listener (§5), and
 * `DocumentSnapshot` never escapes this layer.
 */
interface RotationRepository {

    /**
     * Streams the override in force for [weekId] (`TimeProvider.weekId()`, e.g.
     * `"2026-W40"`), or `null` when there is none.
     *
     * Resolution is by document id: `rotations/{weekId}` wins if it exists,
     * otherwise `rotations/default_schedule`, otherwise `null`.
     *
     * **Never fails.** A missing document, a permission error and an offline
     * device all emit `null`, because the caller's fallback is the pure formula —
     * surfacing an error here would break a screen that has a correct answer
     * available. This is the "seamless offline" contract.
     */
    fun observeActiveSchedule(weekId: String): Flow<RotationSchedule?>
}

/** Firestore-backed [RotationRepository]. */
class FirestoreRotationRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : RotationRepository {

    override fun observeActiveSchedule(weekId: String): Flow<RotationSchedule?> =
        combine(
            observeDocument(weekId),
            observeDocument(FirestoreDocIds.ROTATION_FALLBACK),
        ) { forThisWeek, fallback -> forThisWeek ?: fallback }

    /**
     * One rotation document as a stream, `null` when it does not exist **or the
     * read fails**. Errors are swallowed deliberately (see the interface KDoc):
     * `close(error)` would propagate into the Home `combine` and blank a screen
     * whose fallback is a pure function.
     */
    private fun observeDocument(docId: String): Flow<RotationSchedule?> = callbackFlow {
        val registration = firestore.collection(FirestoreCollections.ROTATIONS)
            .document(docId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                trySend(
                    snapshot?.takeIf { it.exists() }?.toObject(RotationSchedule::class.java),
                )
            }
        awaitClose { registration.remove() }
    }
}
