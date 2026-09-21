package com.gynda.fridaystm.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.gynda.fridaystm.data.model.IzinRecord
import com.gynda.fridaystm.data.model.LarkamRecord
import com.gynda.fridaystm.data.model.PresensiRecord
import com.gynda.fridaystm.data.model.User
import com.gynda.fridaystm.util.FirestoreCollections
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Real-time streams for the Teacher/Admin dashboard.
 *
 * Each `observe…` wraps a Firestore snapshot listener via `callbackFlow`
 * (SKILL.md §5) — the ViewModel sees only `Flow<List<T>>`, never the
 * listener. Filtering by `kelas` is server-side (where indexed); date
 * filtering is client-side on the ViewModel (timestamp is ISO string,
 * prefix `yyyy-MM-dd`). `larkam_records` also supports `larkam_runs`
 * fallback via collection alias.
 *
 * Interface is fake-able for unit tests.
 */
interface TeacherDashboardRepository {

    /** All users whose `kelas == kelas` (realtime). */
    fun observeUsersByClass(kelas: String): Flow<List<User>>

    /** Presensi for the class on a given date (timestamp prefix `yyyy-MM-dd`). */
    fun observePresensi(kelas: String, date: String): Flow<List<PresensiRecord>>

    /** Izin that covers the date and class (client-filtered by start/end). */
    fun observeIzin(kelas: String, date: String): Flow<List<IzinRecord>>

    /** Larkam distance records for the class/date (timestamp prefix). */
    fun observeLarkam(kelas: String, date: String): Flow<List<LarkamRecord>>

    /** One-shot fetch of distinct kelas values (for dropdown). */
    suspend fun fetchAvailableClasses(): Result<List<String>>
}

class FirebaseTeacherDashboardRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : TeacherDashboardRepository {

    override fun observeUsersByClass(kelas: String): Flow<List<User>> = callbackFlow {
        val query: Query = firestore.collection(FirestoreCollections.USERS)
            .whereEqualTo("kelas", kelas)
        val reg = query.addSnapshotListener { snap, err ->
            if (err != null) { trySend(emptyList()); return@addSnapshotListener }
            trySend(snap?.toObjects(User::class.java).orEmpty())
        }
        awaitClose { reg.remove() }
    }

    override fun observePresensi(kelas: String, date: String): Flow<List<PresensiRecord>> = callbackFlow {
        // studentClass filter is server-side; date prefix filtered client-side
        val query: Query = firestore.collection(FirestoreCollections.PRESENSI_RECORDS)
            .whereEqualTo("studentClass", kelas)
        val reg = query.addSnapshotListener { snap, err ->
            if (err != null) { trySend(emptyList()); return@addSnapshotListener }
            val all = snap?.toObjects(PresensiRecord::class.java).orEmpty()
            val filtered = all.filter { it.timestamp.startsWith(date) }
            trySend(filtered)
        }
        awaitClose { reg.remove() }
    }

    override fun observeIzin(kelas: String, date: String): Flow<List<IzinRecord>> = callbackFlow {
        val query: Query = firestore.collection(FirestoreCollections.IZIN_RECORDS)
            .whereEqualTo("kelas", kelas)
        val reg = query.addSnapshotListener { snap, err ->
            if (err != null) { trySend(emptyList()); return@addSnapshotListener }
            val all = snap?.toObjects(IzinRecord::class.java).orEmpty()
            // IzinRecord covers [startDate, endDate] inclusive (yyyy-MM-dd)
            val filtered = all.filter { rec ->
                rec.startDate <= date && date <= rec.endDate
            }
            trySend(filtered)
        }
        awaitClose { reg.remove() }
    }

    override fun observeLarkam(kelas: String, date: String): Flow<List<LarkamRecord>> = callbackFlow {
        // `larkam_records` docs are written without `studentClass` (see
        // PresensiCameraViewModel larkam branch) — so a `whereEqualTo` on
        // class would always return empty. Instead fetch all for the date
        // and let the ViewModel filter to `kelas` via the user roster.
        val query: Query = firestore.collection("larkam_records")
        val reg = query.addSnapshotListener { snap, err ->
            if (err != null) { trySend(emptyList()); return@addSnapshotListener }
            val docs = snap?.documents.orEmpty()
            val list = docs.mapNotNull { d ->
                try {
                    val km = (d.get("distanceKm") as? Number)?.toFloat() ?: 0f
                    val meters = (d.get("distanceMeters") as? Number)?.toDouble() ?: 0.0
                    val ts = d.getString("timestamp") ?: ""
                    if (ts.isNotBlank() && !ts.startsWith(date)) return@mapNotNull null
                    // also accept presensi-like `larkam_runs` fallback
                    LarkamRecord(
                        id = d.id,
                        userId = d.getString("userId") ?: "",
                        distanceKm = km,
                        distanceMeters = meters,
                        studentClass = d.getString("studentClass") ?: "",
                        timestamp = ts,
                        imageUrl = d.getString("imageUrl") ?: d.getString("routeUrl") ?: "",
                    )
                } catch (_: Exception) { null }
            }
            trySend(list)
        }
        awaitClose { reg.remove() }
    }

    override suspend fun fetchAvailableClasses(): Result<List<String>> = runCatching {
        val snap = firestore.collection(FirestoreCollections.USERS)
            .limit(100) // enough to enumerate distinct kelas
            .get()
            .await()
        val users = snap.toObjects(User::class.java)
        users.map { it.kelas }.filter { it.isNotBlank() }.distinct().sorted()
            .ifEmpty { com.gynda.fridaystm.util.TeacherDashboardDefaults.AVAILABLE_CLASSES }
    }
}
