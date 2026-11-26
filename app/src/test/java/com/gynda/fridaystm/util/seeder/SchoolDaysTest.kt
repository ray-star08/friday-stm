package com.gynda.fridaystm.util.seeder

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Demo attendance calendar: weekdays only, starting from a fixed August date. */
class SchoolDaysTest {

    @Test
    fun `thirty school days from august skip weekends`() {
        val days = schoolDays(LocalDate.of(2026, 8, 3), 30)

        assertEquals(30, days.size)
        assertEquals(LocalDate.of(2026, 8, 3), days.first())
        assertEquals(LocalDate.of(2026, 9, 11), days.last())
        assertTrue(days.all { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY })
    }
}
