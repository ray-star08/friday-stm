package com.gynda.fridaystm.util.seeder

import com.gynda.fridaystm.util.TeacherDashboardDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FirestoreDataSeederTest {

    @Test
    fun seedMockData_populatesUsersAndRecordsCorrectly() {
        val expectedClasses = TeacherDashboardDefaults.AVAILABLE_CLASSES
        assertTrue(expectedClasses.contains("XI RPL A"))
        assertTrue(expectedClasses.contains("XI TKJ A"))
        assertTrue(expectedClasses.contains("X RPL A"))
        assertEquals(6, expectedClasses.size)
    }
}
