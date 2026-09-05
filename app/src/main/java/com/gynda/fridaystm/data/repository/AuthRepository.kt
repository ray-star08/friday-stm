package com.gynda.fridaystm.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.gynda.fridaystm.data.model.User
import com.gynda.fridaystm.util.FirestoreCollections
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Authentication + user-profile access, exposed to ViewModels.
 *
 * An interface (SKILL.md §9) so `HomeViewModel` can be unit-tested against a fake
 * without touching Firebase. All Firebase `Task`s are converted with `.await()`
 * and every listener is wrapped in `callbackFlow` — no raw `addOnSuccessListener`
 * / `addAuthStateListener` leaks above this layer (SKILL.md §5).
 */
interface AuthRepository {

    /** The signed-in Firebase uid, or `null` when signed out. */
    val currentUid: String?

    /**
     * Streams the current uid, re-emitting on every sign-in / sign-out.
     * Emits `null` while signed out.
     */
    fun observeAuthState(): Flow<String?>

    /**
     * Signs in with email/password and returns the resolved [User] profile.
     *
     * @return [Result.failure] if auth fails or the profile document is missing.
     */
    suspend fun signIn(email: String, password: String): Result<User>

    /** One-shot fetch of `users/{uid}`. Fails if the document does not exist. */
    suspend fun getUserProfile(uid: String): Result<User>

    /** Streams `users/{uid}`; emits `null` if the document is absent. */
    fun observeUserProfile(uid: String): Flow<User?>

    /** Signs the current user out. */
    fun signOut()
}

/**
 * Firebase-backed [AuthRepository]: Firebase Auth for credentials, Firestore
 * `users/{uid}` for the profile.
 *
 * NIS-based login (task 3.3) is layered on top of this in the UI: the Login
 * screen resolves NIS → email/credential first, then calls [signIn]. This class
 * stays a thin, testable email/password + profile boundary.
 */
class FirebaseAuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : AuthRepository {

    override val currentUid: String?
        get() = auth.currentUser?.uid

    override fun observeAuthState(): Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.uid)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun signIn(email: String, password: String): Result<User> = runCatching {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        val uid = result.user?.uid ?: error("Sign-in succeeded but returned no user.")
        getUserProfile(uid).getOrThrow()
    }

    override suspend fun getUserProfile(uid: String): Result<User> = runCatching {
        val snapshot = firestore.collection(FirestoreCollections.USERS)
            .document(uid)
            .get()
            .await()
        snapshot.toObject(User::class.java)
            ?: error("No profile document for uid=$uid")
    }

    override fun observeUserProfile(uid: String): Flow<User?> = callbackFlow {
        val registration = firestore.collection(FirestoreCollections.USERS)
            .document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObject(User::class.java))
            }
        awaitClose { registration.remove() }
    }

    override fun signOut() = auth.signOut()
}
