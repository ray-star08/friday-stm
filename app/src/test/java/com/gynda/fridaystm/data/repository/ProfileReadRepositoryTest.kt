package com.gynda.fridaystm.data.repository

import com.gynda.fridaystm.data.model.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ProfileReadRepositoryTest {
    @Test fun statsUseSharedDaysAndDoNotHideSourceFailures() = runTest {
        val days = listOf(AttendanceDay("u", "2026-09-18", AttendanceDayStatus.PARTIAL),
            AttendanceDay("u", "2026-09-11", AttendanceDayStatus.LEGACY),
            AttendanceDay("u", "2026-09-04", AttendanceDayStatus.NEEDS_REVIEW))
        val reader = object : AttendanceReadRepository {
            override fun observeUser(userId: String) = flowOf(days)
            override fun observeClass(kelas: String, date: String) = error("Not a class query")
            override suspend fun getUser(userId: String) = Result.success(days)
            override suspend fun getClass(kelas: String, startDate: String, endDate: String) = error("Not a class query")
        }
        val source = object : ProfileAuxiliarySource {
            var fail = false
            override suspend fun distanceKm(uid: String): Double {
                if (fail) error("permission denied")
                return 2.5
            }
            override suspend fun permitCount(uid: String) = 3
        }
        val repository = CompatibleProfileStatsRepository(reader, source)
        val stats = repository.getStats("u").getOrThrow()
        assertEquals(2, stats.presensiCount)
        assertEquals(2.5, stats.larkamDistanceKm, 0.01)
        assertEquals(3, stats.izinCount)
        source.fail = true
        assertTrue(repository.getStats("u").isFailure)
    }
}
