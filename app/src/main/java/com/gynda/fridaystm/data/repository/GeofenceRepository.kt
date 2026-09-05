package com.gynda.fridaystm.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.gynda.fridaystm.data.model.Geofence
import com.gynda.fridaystm.util.FirestoreCollections
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Reads the read-only `geofences` reference collection ("pagar digital").
 *
 * An interface (SKILL.md §9) so `HomeViewModel` can be tested with a fake. The
 * live stream is wrapped in `callbackFlow` — the only sanctioned snapshot
 * listener (SKILL.md §5); `DocumentSnapshot` never escapes this layer.
 */
interface GeofenceRepository {

    /**
     * Streams all geofences, re-emitting on change. Small, static reference data
     * — cached client-side by Firestore, so this is cheap to keep open.
     */
    fun observeGeofences(): Flow<List<Geofence>>
}

/** Firestore-backed [GeofenceRepository]. */
class FirestoreGeofenceRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : GeofenceRepository {

    override fun observeGeofences(): Flow<List<Geofence>> = callbackFlow {
        val registration = firestore.collection(FirestoreCollections.GEOFENCES)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObjects(Geofence::class.java).orEmpty())
            }
        awaitClose { registration.remove() }
    }
}
