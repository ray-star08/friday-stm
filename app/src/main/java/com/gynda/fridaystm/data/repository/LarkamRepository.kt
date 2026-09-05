package com.gynda.fridaystm.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.gynda.fridaystm.data.model.LarkamRun
import com.gynda.fridaystm.util.FirestoreCollections
import kotlinx.coroutines.tasks.await

/**
 * Persists a finished Larkam run to Firestore `larkam_runs`.
 *
 * Firestore is the sole store — no external sync. An interface (SKILL.md §9) so
 * the Larkam ViewModel tests with a fake; the write is `suspend` + `.await()` →
 * [Result] (SKILL.md §5). Role/ownership enforcement lives in Firestore rules.
 */
interface LarkamRepository {

    /** Appends a new run document. Returns failure on any write error. */
    suspend fun logRun(run: LarkamRun): Result<Unit>
}

/** Firestore-backed [LarkamRepository]. */
class DefaultLarkamRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : LarkamRepository {

    override suspend fun logRun(run: LarkamRun): Result<Unit> = runCatching {
        firestore.collection(FirestoreCollections.LARKAM_RUNS)
            .add(
                mapOf(
                    "userId" to run.userId,
                    "distanceMeters" to run.distanceMeters,
                    "elapsedSec" to run.elapsedSec,
                    "path" to run.path,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            )
            .await()
        Unit
    }
}
