package com.gynda.fridaystm.data.repository

import com.gynda.fridaystm.data.model.IzinRecord
import com.gynda.fridaystm.data.model.ReportAggregator
import com.gynda.fridaystm.data.model.User
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReportCompatibilityTest {
    private val user = User(uid = "u1", nama = "Student", kelas = "X")
    private fun epoch(date: String) = LocalDate.parse(date)
        .atStartOfDay(ZoneId.of("Asia/Jakarta")).toInstant().toEpochMilli()

    @Test
    fun lifecycleDaysKeepReviewOutOfPresenceAndPermits() {
        fun day(date: String, status: com.gynda.fridaystm.data.model.AttendanceDayStatus, time: String) =
            com.gynda.fridaystm.data.model.AttendanceDay("u1", date, status,
                attendance = com.gynda.fridaystm.data.model.AttendanceRecord(uid = "u1", date = date,
                    pembiasaan = com.gynda.fridaystm.data.model.PembiasaanStamp(checkedIn = true, valid = true, time = time)))
        val days = listOf(
            day("2026-09-04", com.gynda.fridaystm.data.model.AttendanceDayStatus.COMPLETE, "06:45"),
            day("2026-09-11", com.gynda.fridaystm.data.model.AttendanceDayStatus.PARTIAL, "07:05"),
            day("2026-09-18", com.gynda.fridaystm.data.model.AttendanceDayStatus.LEGACY, "06:50"),
            day("2026-09-25", com.gynda.fridaystm.data.model.AttendanceDayStatus.NEEDS_REVIEW, "06:40"),
        )
        val permits = listOf(IzinRecord(userId = "u1", kelas = "X", tipe = "SAKIT", status = "APPROVED", startDate = "2026-09-25", endDate = "2026-09-25"))
        val report = ReportAggregator.aggregateDays(listOf(user), days, permits, emptyList(), epoch("2026-09-01"), epoch("2026-09-30")).single()
        assertEquals(2, report.totalHadir)
        assertEquals(1, report.totalTerlambat)
        assertEquals(1, report.totalComplete)
        assertEquals(1, report.totalPartial)
        assertEquals(1, report.totalLegacy)
        assertEquals(1, report.totalNeedsReview)
        assertEquals(0, report.totalSakit)
        assertEquals(0, report.totalAlfa)
        assertEquals(days.count { it.countsAsPresent }, report.totalHadir + report.totalTerlambat)
    }

    @Test
    fun rosterAndRangeNarrowTheObservedSessionsBeforeCounting() {
        val day = com.gynda.fridaystm.data.model.AttendanceDay("u1", "2026-09-04", com.gynda.fridaystm.data.model.AttendanceDayStatus.PARTIAL)
        val report = ReportAggregator.aggregateDays(
            listOf(user, user.copy(uid = "u2")),
            listOf(day, day, day.copy(date = "2026-08-28"), day.copy(userId = "foreign", date = "2026-09-11")),
            emptyList(), emptyList(), epoch("2026-09-01"), epoch("2026-09-30"),
        )
        assertEquals(1, report.first { it.studentUid == "u1" }.totalPartial)
        assertEquals(1, report.first { it.studentUid == "u2" }.totalAlfa)
    }

    @Test
    fun compatibilityWrapperUsesJakartaDaysAndSessionSpecificPermitPrecedence() {
        val before = java.util.TimeZone.getDefault()
        try {
            val summaries = listOf("UTC", "America/Los_Angeles", "Asia/Tokyo").map { zone ->
                java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(zone))
                ReportAggregator.aggregate(listOf(user), listOf(
                    com.gynda.fridaystm.data.model.PresensiRecord(userId = "u1", studentClass = "X", timestamp = "2026-09-03T23:45:00Z"),
                    com.gynda.fridaystm.data.model.PresensiRecord(userId = "u1", studentClass = "X", timestamp = "2026-09-04T07:20:00+07:00"),
                ), listOf(IzinRecord(userId = "u1", kelas = "X", tipe = "SAKIT", status = "APPROVED", startDate = "2026-09-04", endDate = "2026-09-05")),
                    emptyList(), epoch("2026-09-04"), epoch("2026-09-05")).single()
            }
            summaries.forEach {
                assertEquals(1, it.totalHadir)
                assertEquals(0, it.totalTerlambat)
                assertEquals(1, it.totalLegacy)
                assertEquals(1, it.totalSakit)
                assertEquals(0, it.totalAlfa)
            }
            assertEquals(1, summaries.distinct().size)
        } finally { java.util.TimeZone.setDefault(before) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun malformedLegacyFailsInsteadOfExportingZero() {
        ReportAggregator.aggregate(listOf(user), listOf(
            com.gynda.fridaystm.data.model.PresensiRecord(userId = "u1", timestamp = "2026-09-04Tinvalid"),
        ), emptyList(), emptyList(), epoch("2026-09-04"), epoch("2026-09-04"))
    }

    @Test
    fun conflictingApprovedPermitsCountOncePerDayWithSakitPriority() {
        val permits = listOf(
            IzinRecord(id = "a", userId = "u1", kelas = "X", tipe = "IZIN", status = "APPROVED", startDate = "2026-09-04", endDate = "2026-09-04"),
            IzinRecord(id = "b", userId = "u1", kelas = "X", tipe = "SAKIT", status = "APPROVED", startDate = "2026-09-04", endDate = "2026-09-04"),
            IzinRecord(id = "c", userId = "u1", kelas = "X", tipe = "SAKIT", status = "APPROVED", startDate = "2026-09-04", endDate = "2026-09-04"),
        )
        for (input in listOf(permits, permits.reversed())) {
            val report = ReportAggregator.aggregate(listOf(user), emptyList(), input, emptyList(), epoch("2026-09-04"), epoch("2026-09-05")).single()
            assertEquals(0, report.totalIzin)
            assertEquals(1, report.totalSakit)
        }
    }
}
