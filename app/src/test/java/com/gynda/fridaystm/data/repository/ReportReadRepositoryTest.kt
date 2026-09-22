package com.gynda.fridaystm.data.repository

import com.gynda.fridaystm.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ReportReadRepositoryTest {
    private class Source : ReportSource {
        var failure: Exception? = null
        override suspend fun users(kelas: String): List<User> {
            failure?.let { throw it }
            return listOf(User(uid = "u1", kelas = "X"), User(uid = "foreign", kelas = "Y"))
        }
        override suspend fun izin(ownerIds: Set<String>) = emptyList<IzinRecord>()
        override suspend fun larkam(ownerIds: Set<String>) = emptyList<LarkamRecord>()
    }
    private class Reader : AttendanceReadRepository {
        var request: List<String>? = null
        var result = Result.success(listOf(AttendanceDay("u1", "2026-09-04", AttendanceDayStatus.PARTIAL)))
        override fun observeUser(userId: String): Flow<List<AttendanceDay>> = flowOf(emptyList())
        override fun observeClass(kelas: String, date: String): Flow<List<AttendanceDay>> = flowOf(emptyList())
        override suspend fun getUser(userId: String) = error("Not a user export")
        override suspend fun getClass(kelas: String, startDate: String, endDate: String): Result<List<AttendanceDay>> {
            request = listOf(kelas, startDate, endDate)
            return result
        }
    }
    private val start = Instant.parse("2026-09-03T17:00:00Z").toEpochMilli()
    private val end = Instant.parse("2026-09-04T17:00:00Z").toEpochMilli()

    @Test fun reportUsesSharedDaysWithJakartaBoundsAndCurrentRoster() = runTest {
        val reader = Reader()
        val result = CompatibleReportRepository(Source(), reader).getRekapSummary("X", start, end).getOrThrow()
        assertEquals(listOf("X", "2026-09-04", "2026-09-05"), reader.request)
        assertEquals(1, result.size)
        assertEquals(1, result.single().totalPartial)
        assertEquals(0, result.single().totalHadir)
        assertEquals(1, result.single().totalUnknownTime)
    }
}
