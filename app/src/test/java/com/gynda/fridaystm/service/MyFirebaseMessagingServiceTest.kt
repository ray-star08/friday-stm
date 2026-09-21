package com.gynda.fridaystm.service

import com.gynda.fridaystm.data.repository.FcmTokenRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test for FCM token sync — verifies that `onNewToken` persists the
 * token to `users/{userId}.fcmToken` via [FcmTokenRepository] (SKILL.md §9:
 * fake, no Firebase, no Android framework).
 *
 * The service is exercised through its internal `handleNewToken` suspend
 * delegator so no `Robolectric`/`FirebaseMessagingService` lifecycle is needed.
 */
class MyFirebaseMessagingServiceTest {

    private class FakeFcmTokenRepository(
        var shouldFail: Boolean = false,
    ) : FcmTokenRepository {
        var lastUserId: String? = null
        var lastToken: String? = null
        var updateCalls = 0

        override suspend fun updateToken(userId: String, token: String): Result<Unit> {
            updateCalls++
            lastUserId = userId
            lastToken = token
            return if (shouldFail) Result.failure(IllegalStateException("firestore failed"))
            else Result.success(Unit)
        }
    }

    @Test
    fun onNewToken_updatesTokenInFirestore() = runTest {
        val fakeRepo = FakeFcmTokenRepository()
        val service = MyFirebaseMessagingService().apply {
            tokenRepository = fakeRepo
            currentUidProvider = { "user123" }
        }

        val token = "fcm_token_abc_123"
        val result = service.handleNewToken(token)

        assertTrue(result.isSuccess)
        assertEquals(1, fakeRepo.updateCalls)
        assertEquals("user123", fakeRepo.lastUserId)
        assertEquals(token, fakeRepo.lastToken)
    }

    @Test
    fun onNewToken_noSignedInUser_returnsFailureWithoutWrite() = runTest {
        val fakeRepo = FakeFcmTokenRepository()
        val service = MyFirebaseMessagingService().apply {
            tokenRepository = fakeRepo
            currentUidProvider = { null }
        }

        val result = service.handleNewToken("some_token")

        assertTrue(result.isFailure)
        assertEquals(0, fakeRepo.updateCalls)
    }

    @Test
    fun onNewToken_blankToken_returnsFailureWithoutWrite() = runTest {
        val fakeRepo = FakeFcmTokenRepository()
        val service = MyFirebaseMessagingService().apply {
            tokenRepository = fakeRepo
            currentUidProvider = { "user123" }
        }

        val result = service.handleNewToken("   ")

        assertTrue(result.isFailure)
        assertEquals(0, fakeRepo.updateCalls)
    }
}
