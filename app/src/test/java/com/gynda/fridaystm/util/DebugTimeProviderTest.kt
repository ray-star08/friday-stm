package com.gynda.fridaystm.util

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Test

class DebugTimeProviderTest {

    @Test
    fun advanceWeek_shiftsByOneWeekAndSnapsToFridayMorning() {
        val provider = DebugTimeProvider()
        val initial = provider.now()
        
        provider.advanceWeek(1)
        val advanced = provider.now()

        assertEquals(DayOfWeek.FRIDAY, advanced.dayOfWeek)
        assertEquals(7, advanced.hour)
        assertEquals(0, advanced.minute)
    }

    @Test
    fun setFridayPhase_setsCorrectTimeOnFriday() {
        val provider = DebugTimeProvider()
        provider.setFridayPhase(8, 15)
        val current = provider.now()

        assertEquals(DayOfWeek.FRIDAY, current.dayOfWeek)
        assertEquals(8, current.hour)
        assertEquals(15, current.minute)
    }
}
