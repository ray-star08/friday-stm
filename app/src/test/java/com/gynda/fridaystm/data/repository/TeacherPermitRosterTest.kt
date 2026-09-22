package com.gynda.fridaystm.data.repository

import com.gynda.fridaystm.data.model.IzinRecord
import com.gynda.fridaystm.data.model.User
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TeacherPermitRosterTest {
    @Test fun transferredInHistoricalPermitIsReadWithoutDecodingTransferredOutEvidence() = runTest {
        val gateway = FilteringAttendanceGateway().apply {
            add("users", mapOf("kelas" to "XI A"), User(uid = "alice", kelas = "XI A"))
            add("izin_records", mapOf("userId" to "alice", "kelas" to "XI B"), IzinRecord(
                id = "old", userId = "alice", kelas = "XI B", startDate = "2026-09-18", endDate = "2026-09-18"))
            add("izin_records", mapOf("userId" to "alice", "kelas" to "XI B"), IzinRecord(
                id = "outside-date", userId = "alice", kelas = "XI B", startDate = "2026-09-11", endDate = "2026-09-11"))
            documents += FilteringAttendanceGateway.Document("izin_records", mapOf("userId" to "bob", "kelas" to "XI A")) {
                error("Foreign permit is malformed")
            }
        }
        val outputs = FirestoreTeacherPermitSource(gateway).observe("XI A", "2026-09-18").toList()
        assertEquals(listOf("old"), outputs.last().map { it.id })
        assertEquals(listOf(AttendanceQuery("izin_records", listOf(AttendanceQueryFilter("userId", "alice")))),
            gateway.requests.filter { it.collection == "izin_records" })
    }

    @Test fun changedRosterCancelsOldOwnerAndWaitsForNewSnapshot() = runTest {
        val gateway = FilteringAttendanceGateway().apply {
            live = true
            add("users", mapOf("kelas" to "XI A"), User(uid = "old", kelas = "XI A"))
            add("izin_records", mapOf("userId" to "old", "kelas" to "XI A"), IzinRecord(
                id = "old", userId = "old", startDate = "2026-09-18", endDate = "2026-09-18"))
            add("izin_records", mapOf("userId" to "new", "kelas" to "XI B"), IzinRecord(
                id = "new", userId = "new", startDate = "2026-09-18", endDate = "2026-09-18"))
        }
        val outputs = mutableListOf<List<IzinRecord>>()
        val job = backgroundScope.launch { FirestoreTeacherPermitSource(gateway).observe("XI A", "2026-09-18").collect { outputs += it } }
        runCurrent(); gateway.emit("users"); runCurrent(); gateway.emit("izin_records"); runCurrent()
        assertEquals(listOf("old"), outputs.last().map { it.id })
        gateway.documents.removeAll { it.collection == "users" }
        gateway.add("users", mapOf("kelas" to "XI A"), User(uid = "new", kelas = "XI A"))
        val completed = outputs.size
        gateway.emit("users"); runCurrent()
        assertEquals("No synthetic empty permits before read completes", completed, outputs.size)
        assertTrue(gateway.removed.contains(AttendanceQuery("izin_records", listOf(AttendanceQueryFilter("userId", "old")))))
        gateway.emit("izin_records"); runCurrent()
        assertEquals(listOf("new"), outputs.last().map { it.id })
        gateway.documents.removeAll { it.collection == "users" }
        gateway.emit("users"); runCurrent()
        assertTrue(outputs.last().isEmpty())
        job.cancel(); runCurrent()
        assertTrue(gateway.removed.contains(AttendanceQuery("izin_records", listOf(AttendanceQueryFilter("userId", "new")))))
    }

    @Test fun livePermitFailureAndCancellationReachCollectorAndRemoveListeners() = runTest {
        for (failure in listOf(IllegalStateException("denied"), kotlinx.coroutines.CancellationException("cancelled"))) {
            val gateway = FilteringAttendanceGateway().apply {
                live = true
                add("users", mapOf("kelas" to "XI A"), User(uid = "alice", kelas = "XI A"))
                documents += FilteringAttendanceGateway.Document("izin_records", mapOf("userId" to "alice")) { throw failure }
            }
            var caught: Throwable? = null
            backgroundScope.launch {
                try { FirestoreTeacherPermitSource(gateway).observe("XI A", "2026-09-18").collect {} }
                catch (e: Throwable) { caught = e }
            }
            runCurrent(); gateway.emit("users"); runCurrent(); gateway.emit("izin_records"); runCurrent()
            assertEquals(failure.javaClass, caught?.javaClass)
            assertEquals(failure.message, caught?.message)
            assertTrue(gateway.removed.contains(rosterQuery("XI A")))
            assertTrue(gateway.removed.contains(AttendanceOwnerCollection.PERMITS.query("alice")))
        }
    }

    @Test fun rosterFailureAndCancellationReachCollector() = runTest {
        for (failure in listOf(IllegalStateException("roster denied"), kotlinx.coroutines.CancellationException("roster cancelled"))) {
            val gateway = FilteringAttendanceGateway().apply {
                live = true
                documents += FilteringAttendanceGateway.Document("users", mapOf("kelas" to "XI A")) { throw failure }
            }
            var caught: Throwable? = null
            backgroundScope.launch {
                try { FirestoreTeacherPermitSource(gateway).observe("XI A", "2026-09-18").collect {} }
                catch (e: Throwable) { caught = e }
            }
            runCurrent(); gateway.emit("users"); runCurrent()
            assertEquals(failure.javaClass, caught?.javaClass)
            assertEquals(failure.message, caught?.message)
            assertEquals(setOf("users"), gateway.requests.map { it.collection }.toSet())
        }
    }

    @Test fun populatedRosterDoesNotEmitSyntheticEmptyPermits() = runTest {
        val gateway = FilteringAttendanceGateway().apply {
            live = true
            add("users", mapOf("kelas" to "XI A"), User(uid = "a", kelas = "XI A"))
        }
        val outputs = mutableListOf<List<IzinRecord>>()
        backgroundScope.launch { FirestoreTeacherPermitSource(gateway).observe("XI A", "2026-09-18").collect { outputs += it } }
        runCurrent(); gateway.emit("users"); runCurrent()
        assertTrue("Permits are not loaded yet", outputs.isEmpty())
        gateway.emit("izin_records"); runCurrent()
        assertEquals(listOf(emptyList<IzinRecord>()), outputs)
    }

    @Test fun emptyClassEmitsEmptyWithoutPermitQuery() = runTest {
        val gateway = FilteringAttendanceGateway()
        assertEquals(listOf(emptyList<IzinRecord>()), FirestoreTeacherPermitSource(gateway).observe("XI A", "2026-09-18").toList())
        assertEquals(listOf(rosterQuery("XI A")), gateway.requests)
    }
}
