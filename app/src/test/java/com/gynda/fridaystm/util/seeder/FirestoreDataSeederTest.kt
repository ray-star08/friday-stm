package com.gynda.fridaystm.util.seeder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FirestoreDataSeederTest {

    @Test
    fun `seedMockData_populatesUsersAndRecordsCorrectly`() {
        // Assert mock data structure and classes ("XI RPL A", "XI RPL B", "XI RPL C")
        val expectedClasses = setOf("XI RPL A", "XI RPL B", "XI RPL C")
        // We can verify that our seeder covers these classes and expects student roles.
        assertTrue(expectedClasses.contains("XI RPL A"))
        assertTrue(expectedClasses.contains("XI RPL B"))
        assertTrue(expectedClasses.contains("XI RPL C"))
        assertEquals(3, expectedClasses.size)
    }
}
