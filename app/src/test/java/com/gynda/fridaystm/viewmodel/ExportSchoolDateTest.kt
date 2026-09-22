package com.gynda.fridaystm.viewmodel

import org.junit.Assert.*
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.TimeZone

class ExportSchoolDateTest {
    @Test fun defaultBoundsAreSchoolMidnightNotDeviceMidnight() {
        val before = TimeZone.getDefault()
        try {
            for (deviceZone in listOf("Asia/Makassar", "America/Los_Angeles")) {
                TimeZone.setDefault(TimeZone.getTimeZone(deviceZone))
                for ((instant, expectedDate) in listOf(
                    "2026-09-03T16:30:00Z" to "2026-09-03", // Makassar already tomorrow
                    "2026-09-03T17:30:00Z" to "2026-09-04", // LA still yesterday
                )) {
                    val clock = Clock.fixed(Instant.parse(instant), ZoneId.of(deviceZone))
                    val start = Instant.ofEpochMilli(ExportReportUiState.defaultStartDate(clock)).atZone(ZoneId.of("Asia/Jakarta"))
                    val end = Instant.ofEpochMilli(ExportReportUiState.defaultEndDate(clock)).atZone(ZoneId.of("Asia/Jakarta"))
                    assertEquals(deviceZone, LocalTime.MIDNIGHT, end.toLocalTime())
                    assertEquals(deviceZone, LocalTime.MIDNIGHT, start.toLocalTime())
                    assertEquals(LocalDate.parse(expectedDate), end.toLocalDate())
                    assertEquals(start.toLocalDate().plusDays(7), end.toLocalDate())
                }
            }
        } finally { TimeZone.setDefault(before) }
    }
}
