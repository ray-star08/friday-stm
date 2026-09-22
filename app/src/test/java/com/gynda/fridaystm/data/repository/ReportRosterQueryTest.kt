package com.gynda.fridaystm.data.repository

import com.gynda.fridaystm.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReportRosterQueryTest {
    private val day = LocalDate.parse("2026-09-18").atStartOfDay(ZoneId.of("Asia/Jakarta")).toInstant().toEpochMilli()
    private val noAttendance = object : AttendanceReadRepository {
        override fun observeUser(userId: String): Flow<List<AttendanceDay>> = error("not realtime")
        override fun observeClass(kelas: String, date: String): Flow<List<AttendanceDay>> = error("not realtime")
        override suspend fun getUser(userId: String): Result<List<AttendanceDay>> = error("not owner")
        override suspend fun getClass(kelas: String, startDate: String, endDate: String) = Result.success(emptyList<AttendanceDay>())
    }

    @Test fun requiredOwnerQueryErrorsFailExportAndCancellationIsRethrown() = runTest {
        for (collection in listOf("users", "izin_records", "larkam_records")) {
            for (failure in listOf(IllegalStateException("denied $collection"), kotlinx.coroutines.CancellationException("cancelled $collection"))) {
                val gateway = FilteringAttendanceGateway().apply {
                    add("users", mapOf("kelas" to "XI A"), User(uid = "alice", kelas = "XI A"))
                    documents += FilteringAttendanceGateway.Document(collection,
                        mapOf("kelas" to "XI A", "userId" to "alice")) { throw failure }
                }
                val result = runCatching {
                    CompatibleReportRepository(FirebaseReportSource(gateway), noAttendance).getRekapSummary("XI A", day, day)
                }
                if (failure is kotlinx.coroutines.CancellationException) assertSame(failure, result.exceptionOrNull())
                else assertSame(failure, result.getOrThrow().exceptionOrNull())
            }
        }
    }

    @Test fun transferredInPermitUsesCurrentOwnerInsteadOfHistoricalClass() = runTest {
        val gateway = FilteringAttendanceGateway().apply {
            add("users", mapOf("kelas" to "XI A"), User(uid = "alice", kelas = "XI A"))
            add("izin_records", mapOf("userId" to "alice", "kelas" to "XI B"), IzinRecord(
                id = "old-permit", userId = "alice", kelas = "XI B", status = "APPROVED",
                startDate = "2026-09-18", endDate = "2026-09-18", tipe = "IZIN"))
            documents += FilteringAttendanceGateway.Document("izin_records", mapOf("userId" to "bob", "kelas" to "XI A")) {
                error("Transferred-out permit must never be decoded")
            }
        }
        val result = CompatibleReportRepository(FirebaseReportSource(gateway), noAttendance)
            .getRekapSummary("XI A", day, day).getOrThrow().single()
        assertEquals(1, result.totalIzin)
        assertEquals(listOf(AttendanceQuery("izin_records", listOf(AttendanceQueryFilter("userId", "alice")))),
            gateway.requests.filter { it.collection == "izin_records" })
    }

    @Test fun emptyRosterExportsEmptyWithoutAnyEvidenceQueries() = runTest {
        val gateway = FilteringAttendanceGateway()
        val failIfCalled = object : AttendanceReadRepository by noAttendance {
            override suspend fun getClass(kelas: String, startDate: String, endDate: String): Result<List<AttendanceDay>> =
                error("Empty roster must not query attendance")
        }
        val result = CompatibleReportRepository(FirebaseReportSource(gateway), failIfCalled).getRekapSummary("XI A", day, day)
        assertEquals(emptyList<StudentSummaryReport>(), result.getOrThrow())
        assertEquals(listOf(rosterQuery("XI A")), gateway.requests)
    }

    @Test fun malformedForeignLarkamIsNeverDecodedAndCannotDenyClassExport() = runTest {
        var foreignDecodes = 0
        val gateway = FilteringAttendanceGateway().apply {
            add("users", mapOf("kelas" to "XI A"), User(uid = "alice", kelas = "XI A"))
            add("larkam_records", mapOf("userId" to "alice"), ReportLarkamDocument(
                userId = "alice", distanceInMeters = 1250L, timestamp = "2026-09-18T06:40:00", imageUrl = "own.jpg"))
            documents += FilteringAttendanceGateway.Document("larkam_records", mapOf("userId" to "bob")) {
                foreignDecodes++
                error("Malformed foreign timestamp/imageUrl: expected String, got map")
            }
        }
        val result = CompatibleReportRepository(FirebaseReportSource(gateway), noAttendance)
            .getRekapSummary("XI A", day, day)
        assertTrue("Foreign data must not break export: ${result.exceptionOrNull()}", result.isSuccess)
        assertEquals(0, foreignDecodes)
        assertEquals(1250.0, result.getOrThrow().single().totalLarkamDistanceMeters, 0.0)
        assertEquals(listOf(AttendanceQuery("larkam_records", listOf(AttendanceQueryFilter("userId", "alice")))),
            gateway.requests.filter { it.collection == "larkam_records" })
    }
}
