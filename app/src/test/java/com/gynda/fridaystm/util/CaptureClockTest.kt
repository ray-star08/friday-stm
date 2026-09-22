package com.gynda.fridaystm.util

import org.junit.Assert.*
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class CaptureClockTest {
    @Test fun `capture clock uses school time regardless of device zone`() {
        val instant = Instant.parse("2026-08-13T23:45:00Z")
        for (zone in listOf("UTC", "Asia/Makassar", "America/Los_Angeles")) {
            val time = SystemTimeProvider(Clock.fixed(instant, ZoneId.of(zone)))
            assertEquals(LocalDateTime.of(2026, 8, 14, 6, 45), time.now())
            assertEquals(com.gynda.fridaystm.domain.FridayPhase.PEMBIASAAN, com.gynda.fridaystm.domain.resolvePhase(time.now()))
        }
    }
}
