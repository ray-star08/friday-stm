package com.gynda.fridaystm.data.repository

import com.gynda.fridaystm.data.model.PresensiRecord
import com.gynda.fridaystm.data.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** Query fixtures filter raw fields BEFORE decoding, just as Firestore does. */
internal class FilteringAttendanceGateway : AttendanceQueryGateway {
    data class Document(val collection: String, val fields: Map<String, String>, val decode: () -> Any)
    val documents = mutableListOf<Document>()
    val requests = mutableListOf<AttendanceQuery>()
    val removed = mutableListOf<AttendanceQuery>()
    var live = false
    private val updates = mutableMapOf<String, MutableSharedFlow<Unit>>()
    suspend fun emit(collection: String) { updates.getOrPut(collection) { MutableSharedFlow() }.emit(Unit) }

    fun add(collection: String, fields: Map<String, String>, value: Any) {
        documents += Document(collection, fields) { value }
    }

    private fun <T : Any> read(query: AttendanceQuery, type: Class<T>): List<T> {
        requests += query
        return documents.filter { doc ->
            doc.collection == query.collection && query.filters.all { filter ->
                val value = doc.fields[filter.field]
                value != null && when (filter.operator) {
                    "==" -> value == filter.value
                    ">=" -> value >= filter.value
                    "<=" -> value <= filter.value
                    else -> error("Unsupported fixture operator")
                }
            }
        }.map { checkNotNull(type.cast(it.decode())) }
    }

    override suspend fun <T : Any> get(query: AttendanceQuery, type: Class<T>): List<T> = read(query, type)
    override fun <T : Any> observe(query: AttendanceQuery, type: Class<T>): Flow<List<T>> = if (!live) flowOf(read(query, type)) else flow {
        requests += query
        try {
            updates.getOrPut(query.collection) { MutableSharedFlow() }.collect { emit(read(query, type)) }
        } finally { removed += query }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RosterQueryPolicyTest {
    @Test fun populatedRosterMustWaitForBothEvidenceSnapshots() = runTest {
        val gateway = FilteringAttendanceGateway().apply {
            live = true
            add("users", mapOf("kelas" to "XI A"), User(uid = "a", kelas = "XI A"))
        }
        val outputs = mutableListOf<AttendanceReadSnapshot>()
        backgroundScope.launch {
            FirestoreAttendanceReadSource(gateway).observe(AttendanceReadScope.ClassRange("XI A", "2026-09-18", "2026-09-18"))
                .collect { outputs += it }
        }
        runCurrent(); gateway.emit("users"); runCurrent()
        assertTrue("Roster alone is not proof of zero attendance", outputs.isEmpty())
        gateway.emit("attendance"); runCurrent()
        assertTrue("Legacy query is still loading", outputs.isEmpty())
        gateway.emit("presensi_records"); runCurrent()
        assertEquals(1, outputs.size)
    }

    @Test fun emptyOneShotRosterDoesNotReadAnyEvidence() = runTest {
        val gateway = FilteringAttendanceGateway()
        assertEquals(AttendanceReadSnapshot(), FirestoreAttendanceReadSource(gateway)
            .get(AttendanceReadScope.ClassRange("XI A", "2026-09-18", "2026-09-18")))
        assertEquals(listOf(rosterQuery("XI A")), gateway.requests)
    }

    @Test fun everyCurrentOwnerGetsExactQueryAndDuplicateProfilesDoNotDuplicateEvidence() = runTest {
        val gateway = FilteringAttendanceGateway().apply {
            for (uid in listOf("a", "b", "a", "")) {
                add("users", mapOf("kelas" to "XI A"), User(uid = uid, kelas = "XI A"))
            }
            for (uid in listOf("a", "b")) add("presensi_records", mapOf("userId" to uid, "studentClass" to "old"),
                PresensiRecord(id = uid, userId = uid))
        }
        val result = FirestoreAttendanceReadSource(gateway).get(AttendanceReadScope.ClassRange("XI A", "2026-09-18", "2026-09-18"))
        assertEquals(listOf("a", "b"), result.presensi.map { it.id })
        assertEquals(listOf("a", "b").map { AttendanceOwnerCollection.LEGACY.query(it) },
            gateway.requests.filter { it.collection == "presensi_records" })
    }

    @Test fun liveLegacyFailurePropagatesAndRemovesRosterAndOwnerListeners() = runTest {
        for (failure in listOf(IllegalStateException("denied"), CancellationException("cancelled"))) {
            val gateway = FilteringAttendanceGateway().apply {
                live = true
                add("users", mapOf("kelas" to "XI A"), User(uid = "a", kelas = "XI A"))
                documents += FilteringAttendanceGateway.Document("presensi_records", mapOf("userId" to "a")) { throw failure }
            }
            var caught: Throwable? = null
            val job = backgroundScope.launch {
                try {
                    FirestoreAttendanceReadSource(gateway).observe(AttendanceReadScope.ClassRange("XI A", "2026-09-18", "2026-09-18"))
                        .collect {}
                } catch (e: Throwable) { caught = e }
            }
            runCurrent(); gateway.emit("users"); runCurrent(); gateway.emit("presensi_records"); runCurrent()
            assertEquals(failure.javaClass, caught?.javaClass)
            assertEquals(failure.message, caught?.message)
            assertTrue(gateway.removed.contains(rosterQuery("XI A")))
            assertTrue(gateway.removed.contains(AttendanceOwnerCollection.LEGACY.query("a")))
            job.cancel()
        }
    }

    @Test fun rosterAndCanonicalFailuresReachCollectorWithoutWaitingForLegacy() = runTest {
        for (collection in listOf("users", "attendance")) {
            for (failure in listOf(IllegalStateException("denied"), CancellationException("cancelled"))) {
                val gateway = FilteringAttendanceGateway().apply {
                    live = true
                    add("users", mapOf("kelas" to "XI A"), User(uid = "a", kelas = "XI A"))
                    documents += FilteringAttendanceGateway.Document(collection, mapOf("kelas" to "XI A", "date" to "2026-09-18")) { throw failure }
                }
                var caught: Throwable? = null
                backgroundScope.launch {
                    try { FirestoreAttendanceReadSource(gateway).observe(AttendanceReadScope.ClassRange("XI A", "2026-09-18", "2026-09-18")).collect {} }
                    catch (e: Throwable) { caught = e }
                }
                runCurrent(); gateway.emit("users"); runCurrent(); gateway.emit("attendance"); runCurrent()
                assertEquals(failure.javaClass, caught?.javaClass)
                assertEquals(failure.message, caught?.message)
                assertTrue(gateway.removed.contains(rosterQuery("XI A")))
            }
        }
    }

    @Test fun realtimeTransferCancelsPreviousSubscriptionsAndWaitsForNewEvidence() = runTest {
        val gateway = FilteringAttendanceGateway().apply {
            live = true
            add("users", mapOf("kelas" to "XI A"), User(uid = "old", kelas = "XI A"))
            add("presensi_records", mapOf("userId" to "old", "studentClass" to "XI A"), PresensiRecord(id = "old", userId = "old"))
            add("presensi_records", mapOf("userId" to "new", "studentClass" to "XI B"), PresensiRecord(id = "new", userId = "new"))
        }
        val outputs = mutableListOf<AttendanceReadSnapshot>()
        val job = backgroundScope.launch {
            FirestoreAttendanceReadSource(gateway).observe(AttendanceReadScope.ClassRange("XI A", "2026-09-18", "2026-09-18"))
                .collect { outputs += it }
        }
        runCurrent()
        gateway.emit("users"); runCurrent()
        gateway.emit("attendance"); gateway.emit("presensi_records"); runCurrent()
        assertEquals(listOf("old"), outputs.last().presensi.map { it.id })
        gateway.documents.removeAll { it.collection == "users" }
        gateway.add("users", mapOf("kelas" to "XI A"), User(uid = "new", kelas = "XI A"))
        val completed = outputs.size
        gateway.emit("users"); runCurrent()
        assertEquals("No synthetic successful-empty snapshot while loading", completed, outputs.size)
        assertTrue(gateway.removed.contains(AttendanceQuery("presensi_records", listOf(AttendanceQueryFilter("userId", "old")))))
        gateway.emit("attendance"); gateway.emit("presensi_records"); runCurrent()
        assertEquals(listOf("new"), outputs.last().presensi.map { it.id })
        job.cancel(); runCurrent()
        assertTrue(gateway.removed.contains(AttendanceQuery("presensi_records", listOf(AttendanceQueryFilter("userId", "new")))))
    }

    @Test fun emptyRealtimeRosterIsExplicitSuccessWithoutReadingAnyEvidence() = runTest {
        val gateway = FilteringAttendanceGateway().apply { live = true }
        val outputs = mutableListOf<AttendanceReadSnapshot>()
        backgroundScope.launch {
            FirestoreAttendanceReadSource(gateway).observe(AttendanceReadScope.ClassRange("XI A", "2026-09-18", "2026-09-18"))
                .collect { outputs += it }
        }
        runCurrent(); gateway.emit("users"); runCurrent()
        assertEquals(listOf(AttendanceReadSnapshot()), outputs)
        assertEquals(setOf("users"), gateway.requests.map { it.collection }.toSet())
    }

    @Test fun transferredInLegacyEvidenceIsFetchedByCurrentRosterOwnerNotCaptureClass() = runTest {
        val gateway = FilteringAttendanceGateway().apply {
            add("users", mapOf("kelas" to "XI A"), User(uid = "moved-in", kelas = "XI A"))
            add("presensi_records", mapOf("userId" to "moved-in", "studentClass" to "XI B"),
                PresensiRecord(id = "old-evidence", userId = "moved-in", studentClass = "XI B", timestamp = "2026-09-18T06:40:00"))
            documents += FilteringAttendanceGateway.Document("presensi_records",
                mapOf("userId" to "moved-out", "studentClass" to "XI A")) {
                error("Foreign legacy timestamp cannot be decoded")
            }
        }
        val snapshot = FirestoreAttendanceReadSource(gateway)
            .get(AttendanceReadScope.ClassRange("XI A", "2026-09-18", "2026-09-18"))
        assertEquals(listOf("old-evidence"), snapshot.presensi.map { it.id })
        assertEquals(listOf(AttendanceQuery("presensi_records", listOf(AttendanceQueryFilter("userId", "moved-in")))),
            gateway.requests.filter { it.collection == "presensi_records" })
    }
}
