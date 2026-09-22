package com.gynda.fridaystm.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.gynda.fridaystm.data.model.IzinRecord
import com.gynda.fridaystm.data.model.LarkamRecord
import com.gynda.fridaystm.data.model.AttendanceDay
import com.gynda.fridaystm.data.model.User
import com.gynda.fridaystm.util.FirestoreCollections
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.buffer
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
    fun observePresensi(kelas: String, date: String): Flow<List<AttendanceDay>>

    /** Izin for current class members, including evidence captured before a transfer. */
    fun observeIzin(kelas: String, date: String): Flow<List<IzinRecord>>

    /** Larkam distance records for the class/date (timestamp prefix). */
    fun observeLarkam(kelas: String, date: String): Flow<List<LarkamRecord>>

    /** One-shot fetch of distinct kelas values (for dropdown). */
    suspend fun fetchAvailableClasses(): Result<List<String>>
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal class FirestoreTeacherPermitSource(private val gateway: AttendanceQueryGateway) {
    fun observe(kelas: String, date: String): Flow<List<IzinRecord>> =
        gateway.observe(rosterQuery(kelas), User::class.java).flatMapLatest { users ->
            val ids = users.currentOwnerIds(kelas)
            if (ids.isEmpty()) flowOf(emptyList()) else gateway.observeForOwners(
                AttendanceOwnerCollection.PERMITS, ids, IzinRecord::class.java,
            ).map { records -> records.filter { it.userId in ids && it.startDate <= date && date <= it.endDate } }
        }.buffer(0).restoreQueryCancellation()
}

class FirebaseTeacherDashboardRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val attendanceReader: AttendanceReadRepository = FirebaseAttendanceReadRepository(firestore),
) : TeacherDashboardRepository {

    override fun observeUsersByClass(kelas: String): Flow<List<User>> = callbackFlow {
        val query: Query = firestore.collection(FirestoreCollections.USERS)
            .whereEqualTo("kelas", kelas)
        val reg = query.addSnapshotListener { snap, err ->
            if (err != null) { close(err); return@addSnapshotListener }
            trySend(snap?.toObjects(User::class.java).orEmpty())
        }
        awaitClose { reg.remove() }
    }

    override fun observePresensi(kelas: String, date: String): Flow<List<AttendanceDay>> =
        attendanceReader.observeClass(kelas, date)

    override fun observeIzin(kelas: String, date: String): Flow<List<IzinRecord>> =
        FirestoreTeacherPermitSource(FirebaseAttendanceQueryGateway(firestore)).observe(kelas, date)

    override fun observeLarkam(kelas: String, date: String): Flow<List<LarkamRecord>> = callbackFlow {
        // `larkam_records` docs are written without `studentClass` (see
        // PresensiCameraViewModel larkam branch) — so a `whereEqualTo` on
        // class would always return empty. Instead fetch all for the date
        // and let the ViewModel filter to `kelas` via the user roster.
        val query: Query = firestore.collection("larkam_records")
        val reg = query.addSnapshotListener { snap, err ->
            if (err != null) { close(err); return@addSnapshotListener }
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
