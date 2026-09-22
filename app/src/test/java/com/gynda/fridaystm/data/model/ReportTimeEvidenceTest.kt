package com.gynda.fridaystm.data.model

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReportTimeEvidenceTest {
    @Test fun missingOrMalformedTimeStaysRecordedButNeverEntersTimeBuckets() {
        val unknown = listOf("", " ", "6:30", "06:7", "24:00", "06:99", "not-a-time", "06:30:00", " 06:30")
        val times = unknown + listOf("00:00", "06:59", "07:00", "23:59")
        val start = LocalDate.parse("2026-09-01")
        val records = times.mapIndexed { index, time ->
            AttendanceRecord(uid = "student", date = start.plusDays(index.toLong()).toString(),
                pembiasaan = PembiasaanStamp(checkedIn = true, valid = true, time = time))
        }
        val days = AttendanceDayProjector.merge(records, emptyList())
        val zone = ZoneId.of("Asia/Jakarta")
        val report = ReportAggregator.aggregateDays(listOf(User(uid = "student")), days, emptyList(), emptyList(),
            start.atStartOfDay(zone).toInstant().toEpochMilli(),
            start.plusDays(times.size.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()).single()
        assertEquals("Only valid times before 07:00", 2, report.totalHadir)
        assertEquals("Only valid times from 07:00", 2, report.totalTerlambat)
        assertEquals(times.size, report.totalPartial)
        assertEquals(0, report.totalAlfa)
        assertEquals(unknown.size, report.totalUnknownTime)
        assertEquals(times, days.sortedBy { it.date }.map { it.time })
    }
}
