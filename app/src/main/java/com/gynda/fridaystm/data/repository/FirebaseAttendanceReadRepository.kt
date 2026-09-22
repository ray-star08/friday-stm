package com.gynda.fridaystm.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.gynda.fridaystm.data.model.AttendanceRecord
import com.gynda.fridaystm.data.model.PresensiRecord
import com.gynda.fridaystm.data.model.User
import com.gynda.fridaystm.util.AttendanceFields
import com.gynda.fridaystm.util.FirestoreCollections
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.tasks.await

/** Firebase implementation of the compatibility read contract. One-shot reads require the server. */
class FirebaseAttendanceReadRepository(
    firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : AttendanceReadRepository by CompatibleAttendanceReadRepository(
    FirestoreAttendanceReadSource(FirebaseAttendanceQueryGateway(firestore)),
)

internal data class AttendanceQuery(val collection: String, val filters: List<AttendanceQueryFilter>)
internal data class AttendanceQueryFilter(val field: String, val value: String, val operator: String = "==")
internal interface AttendanceQueryGateway {
    fun <T : Any> observe(query: AttendanceQuery, type: Class<T>): Flow<List<T>>
    suspend fun <T : Any> get(query: AttendanceQuery, type: Class<T>): List<T>
}
/** Only owner-keyed collections may use roster fan-out; never a class captured on evidence. */
internal enum class AttendanceOwnerCollection(val collection: String) {
    LEGACY(FirestoreCollections.PRESENSI_RECORDS),
    PERMITS(FirestoreCollections.IZIN_RECORDS),
    LARKAM("larkam_records");

    fun query(uid: String): AttendanceQuery {
        require(uid.isNotBlank()) { "UID kosong" }
        return AttendanceQuery(collection, listOf(AttendanceQueryFilter("userId", uid)))
    }
}

internal fun List<User>.currentOwnerIds(kelas: String): Set<String> =
    filter { it.kelas == kelas && it.uid.isNotBlank() }.map { it.uid }.toSet()

internal suspend fun <T : Any> AttendanceQueryGateway.getForOwners(
    collection: AttendanceOwnerCollection,
    ownerIds: Set<String>,
    type: Class<T>,
): List<T> = ownerIds.flatMap { get(collection.query(it), type) }

internal fun rosterQuery(kelas: String) = AttendanceQuery(
    FirestoreCollections.USERS, listOf(AttendanceQueryFilter("kelas", kelas)),
)

// combine/flatMapLatest launch child collectors; an upstream CancellationException
// otherwise only stops that child and leaves the class stream waiting forever.
internal class AttendanceQueryCancelled(val cancellation: CancellationException) : RuntimeException(cancellation)
internal fun <T> Flow<T>.forwardQueryCancellation(): Flow<T> = catch { failure ->
    currentCoroutineContext().ensureActive() // Normal collector/roster replacement cancellation stays normal.
    if (failure is CancellationException) throw AttendanceQueryCancelled(failure)
    throw failure
}
internal fun <T> Flow<T>.restoreQueryCancellation(): Flow<T> = catch { failure ->
    if (failure is AttendanceQueryCancelled) throw failure.cancellation
    throw failure
}

internal fun <T : Any> AttendanceQueryGateway.observeForOwners(
    collection: AttendanceOwnerCollection,
    ownerIds: Set<String>,
    type: Class<T>,
): Flow<List<T>> = if (ownerIds.isEmpty()) flowOf(emptyList()) else
    combine(ownerIds.map { observe(collection.query(it), type).forwardQueryCancellation() }) { batches -> batches.flatMap { it } }

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal class FirestoreAttendanceReadSource(private val gateway: AttendanceQueryGateway) : AttendanceReadSource {
    override fun observe(scope: AttendanceReadScope): Flow<AttendanceReadSnapshot> = when (scope) {
        is AttendanceReadScope.Owner -> combine(
            gateway.observe(ownerAttendance(scope.uid), AttendanceRecord::class.java).forwardQueryCancellation(),
            gateway.observe(AttendanceOwnerCollection.LEGACY.query(scope.uid), PresensiRecord::class.java).forwardQueryCancellation(),
        ) { a, p -> AttendanceReadSnapshot(a, p) }.restoreQueryCancellation()
        is AttendanceReadScope.ClassRange -> gateway.observe(rosterQuery(scope.kelas), User::class.java)
            .flatMapLatest { users ->
                val ids = users.currentOwnerIds(scope.kelas)
                if (ids.isEmpty()) flowOf(AttendanceReadSnapshot(roster = users)) else combine(
                    gateway.observe(classAttendance(scope), AttendanceRecord::class.java).forwardQueryCancellation(),
                    gateway.observeForOwners(AttendanceOwnerCollection.LEGACY, ids, PresensiRecord::class.java),
                ) { a, p -> AttendanceReadSnapshot(a, p, users) }
            }.buffer(0).restoreQueryCancellation()
    }

    override suspend fun get(scope: AttendanceReadScope): AttendanceReadSnapshot = when (scope) {
        is AttendanceReadScope.Owner -> AttendanceReadSnapshot(
            gateway.get(ownerAttendance(scope.uid), AttendanceRecord::class.java),
            gateway.get(AttendanceOwnerCollection.LEGACY.query(scope.uid), PresensiRecord::class.java),
        )
        is AttendanceReadScope.ClassRange -> {
            val users = gateway.get(rosterQuery(scope.kelas), User::class.java)
            val ids = users.currentOwnerIds(scope.kelas)
            if (ids.isEmpty()) AttendanceReadSnapshot(roster = users) else AttendanceReadSnapshot(
                gateway.get(classAttendance(scope), AttendanceRecord::class.java),
                gateway.getForOwners(AttendanceOwnerCollection.LEGACY, ids, PresensiRecord::class.java),
                users,
            )
        }
    }

    private fun ownerAttendance(uid: String) = AttendanceQuery(
        FirestoreCollections.ATTENDANCE, listOf(AttendanceQueryFilter(AttendanceFields.UID, uid)),
    )
    private fun classAttendance(scope: AttendanceReadScope.ClassRange) = AttendanceQuery(
        FirestoreCollections.ATTENDANCE, listOf(
            AttendanceQueryFilter(AttendanceFields.DATE, scope.start, ">="),
            AttendanceQueryFilter(AttendanceFields.DATE, scope.end, "<="),
        ),
    )
}

internal class FirebaseAttendanceQueryGateway(private val firestore: FirebaseFirestore) : AttendanceQueryGateway {
    override fun <T : Any> observe(query: AttendanceQuery, type: Class<T>): Flow<List<T>> = callbackFlow {
        val registration = buildQuery(query).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else {
                try {
                    checkNotNull(snapshot) { "Snapshot kehadiran tidak tersedia" }
                    trySend(snapshot.toObjects(type))
                } catch (e: Exception) {
                    close(e)
                }
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun <T : Any> get(query: AttendanceQuery, type: Class<T>): List<T> =
        buildQuery(query).get(Source.SERVER).await().toObjects(type)

    private fun buildQuery(spec: AttendanceQuery): Query = spec.filters.fold(
        firestore.collection(spec.collection) as Query,
    ) { query, filter ->
        when (filter.operator) {
            "==" -> query.whereEqualTo(filter.field, filter.value)
            ">=" -> query.whereGreaterThanOrEqualTo(filter.field, filter.value)
            "<=" -> query.whereLessThanOrEqualTo(filter.field, filter.value)
            else -> error("Unsupported attendance filter")
        }
    }
}
