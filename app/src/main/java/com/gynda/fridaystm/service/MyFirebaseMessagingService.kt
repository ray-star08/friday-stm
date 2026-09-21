package com.gynda.fridaystm.service

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.gynda.fridaystm.data.repository.FcmTokenRepository
import com.gynda.fridaystm.data.repository.FirebaseFcmTokenRepository
import com.gynda.fridaystm.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * FCM entry point for presensi & larkam reminders.
 *
 * - `onNewToken`: persists the token to `users/{uid}.fcmToken` (so Cloud
 *   Functions / admin tooling can target this device). No-op when no user
 *   is signed in; blank tokens are rejected by the repository.
 * - `onMessageReceived`: always shows a local notification via
 *   [NotificationHelper] — FCM notification messages are suppressed when the
 *   app is foregrounded, so we re-post them ourselves from both notification
 *   and data payloads.
 *
 * Test seam: [tokenRepository]/[auth]/[firestore] are `var`s with setters so
 * `MyFirebaseMessagingServiceTest` can inject fakes without Robolectric.
 * Production code uses the Firebase singletons. `handleNewToken` is a
 * suspending pure delegator that the test drives directly.
 */
class MyFirebaseMessagingService : FirebaseMessagingService() {

    // Test seams — overridden in unit tests via direct field assignment.
    // `currentUidProvider` avoids needing to mock final `FirebaseAuth`.
    // `tokenRepository` is nullable to avoid eagerly instantiating Firebase in
    // JVM unit tests (which would trigger `Process.myPid` not-mocked).
    private var _tokenRepository: FcmTokenRepository? = null
    var tokenRepository: FcmTokenRepository
        get() = _tokenRepository ?: FirebaseFcmTokenRepository()
        set(value) { _tokenRepository = value }
    var currentUidProvider: () -> String? = { FirebaseAuth.getInstance().currentUser?.uid }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        scope.launch {
            handleNewToken(token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val notification = remoteMessage.notification
        val (title, body, target) = NotificationHelper.resolveContent(
            notificationTitle = notification?.title,
            notificationBody = notification?.body,
            data = remoteMessage.data,
        )
        NotificationHelper.showPresensiReminderNotification(
            context = applicationContext,
            title = title,
            body = body,
            target = target,
        )
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Persists [token] for the currently signed-in user.
     *
     * Suspends; the caller ([onNewToken]) launches it. Returns `Result`
     * so the service can log but not crash on Firestore errors. Exposed as
     * `internal` for direct JVM testing without needing to subclass the
     * service (which would require the Android framework).
     */
    internal suspend fun handleNewToken(token: String): Result<Unit> {
        if (token.isBlank()) return Result.failure(IllegalArgumentException("token blank"))
        val uid = currentUidProvider() ?: return Result.failure(IllegalStateException("no signed-in user"))
        return tokenRepository.updateToken(uid, token)
    }
}
