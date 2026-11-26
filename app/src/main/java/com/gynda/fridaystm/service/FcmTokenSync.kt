package com.gynda.fridaystm.service

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.gynda.fridaystm.data.repository.FirebaseFcmTokenRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Best-effort FCM token sync at the UI boundary.
 *
 * Why this exists: [MyFirebaseMessagingService.onNewToken] is a no-op when no
 * user is signed in, and a token minted before the first login (the common
 * fresh-install case) was dropped forever — so most devices never got
 * `users/{uid}.fcmToken` and never received reminders. Conversely, logout left
 * the old token on the previous account, leaking push to the wrong owner.
 *
 * Both functions are best-effort (`runCatching`, IO dispatcher): token sync
 * must never block login/logout or crash the app. ViewModels stay
 * Android-free (SKILL.md); the Firebase calls live here at the edge.
 */
object FcmTokenSync {

    /** Uploads this device's current FCM token to the signed-in user's profile. */
    suspend fun uploadForCurrentUser(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
                ?: error("no signed-in user")
            val token = FirebaseMessaging.getInstance().token.await()
            FirebaseFcmTokenRepository().updateToken(uid, token).getOrThrow()
        }
    }

    /** Removes this device's token from the signed-in user's profile (pre-logout). */
    suspend fun clearForCurrentUser(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
                ?: error("no signed-in user")
            FirebaseFcmTokenRepository().deleteToken(uid).getOrThrow()
        }
    }
}
