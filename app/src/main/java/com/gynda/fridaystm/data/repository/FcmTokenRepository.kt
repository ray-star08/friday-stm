package com.gynda.fridaystm.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.gynda.fridaystm.util.FirestoreCollections
import kotlinx.coroutines.tasks.await

/**
 * Persists the FCM registration token to the user's profile document.
 *
 * Contract: `users/{userId}` field `fcmToken` (see `FcmFields`).
 * An interface (SKILL.md §9) so the messaging service and its tests
 * can fake Firestore — all Firebase `Task`s are converted with `.await()`
 * (SKILL.md §5) and the caller sees only `Result`.
 */
interface FcmTokenRepository {
    /** Updates `fcmToken` for [userId]. Fails if [token] is blank. */
    suspend fun updateToken(userId: String, token: String): Result<Unit>
}

object FcmFields {
    const val FCM_TOKEN = "fcmToken"
}

class FirebaseFcmTokenRepository(
    private val firestoreProvider: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() },
) : FcmTokenRepository {

    override suspend fun updateToken(userId: String, token: String): Result<Unit> = runCatching {
        require(userId.isNotBlank()) { "userId must not be blank" }
        require(token.isNotBlank()) { "fcm token must not be blank" }
        firestoreProvider().collection(FirestoreCollections.USERS)
            .document(userId)
            .set(mapOf(FcmFields.FCM_TOKEN to token), SetOptions.merge())
            .await()
        Unit
    }
}
