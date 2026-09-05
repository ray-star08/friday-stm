package com.gynda.fridaystm.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Pure JVM tests for the deterministic attendance document id (task 3.3, no
 * doubles). The `uid_date` id is what makes a duplicate check-in structurally
 * impossible — a second submit resolves to the *same* document path, so this
 * locks that contract in place. (Firestore's actual no-duplicate write behaviour
 * is verified separately at the emulator level.)
 */
class AttendanceDocIdTest {

    @Test
    fun `docId is exactly uid_date`() {
        assertEquals("u1_2026-08-14", AttendanceRecord.docIdFor("u1", "2026-08-14"))
    }

    @Test
    fun `same uid and date always yields the same id — a dup check-in hits one doc`() {
        assertEquals(
            AttendanceRecord.docIdFor("u1", "2026-08-14"),
            AttendanceRecord.docIdFor("u1", "2026-08-14"),
        )
    }

    @Test
    fun `different date yields a different id`() {
        assertNotEquals(
            AttendanceRecord.docIdFor("u1", "2026-08-14"),
            AttendanceRecord.docIdFor("u1", "2026-08-21"),
        )
    }

    @Test
    fun `different uid yields a different id`() {
        assertNotEquals(
            AttendanceRecord.docIdFor("u1", "2026-08-14"),
            AttendanceRecord.docIdFor("u2", "2026-08-14"),
        )
    }

    @Test
    fun `docId getter matches the companion helper`() {
        assertEquals(
            AttendanceRecord.docIdFor("u1", "2026-08-14"),
            AttendanceRecord(uid = "u1", date = "2026-08-14").docId,
        )
    }
}
