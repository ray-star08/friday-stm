package com.gynda.fridaystm.data.repository

import com.google.firebase.firestore.FieldValue
import com.gynda.fridaystm.data.model.CheckoutStamp
import com.gynda.fridaystm.data.model.PembiasaanStamp
import com.gynda.fridaystm.util.AttendanceFields
import com.gynda.fridaystm.util.AttendanceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for the merge-payload builders (task 3.3, no doubles). These lock
 * the **isolation** contract: each single-phase write must carry only its own
 * fields, so `SetOptions.merge()` can never null out a sibling phase.
 */
class AttendancePayloadsTest {

    private val pembiasaan = PembiasaanStamp(activity = "senam", checkedIn = true, time = "06:45")
    private val checkout = CheckoutStamp(checkedOut = true, time = "07:58")

    // --- pembiasaan --------------------------------------------------------

    @Test
    fun `pembiasaan payload carries exactly its own keys — no checkout, no status`() {
        val p = pembiasaanMergePayload("u1", "2026-08-14", 11, pembiasaan)
        assertEquals(
            setOf(
                AttendanceFields.UID,
                AttendanceFields.DATE,
                AttendanceFields.GRADE,
                AttendanceFields.PEMBIASAAN,
                AttendanceFields.UPDATED_AT,
            ),
            p.keys,
        )
        // Isolation: a Pembiasaan write must never touch the checkout/status keys.
        assertFalse(p.containsKey(AttendanceFields.CHECKOUT))
        assertFalse(p.containsKey(AttendanceFields.STATUS))
    }

    @Test
    fun `pembiasaan payload passes the values through unchanged`() {
        val p = pembiasaanMergePayload("u1", "2026-08-14", 11, pembiasaan)
        assertEquals("u1", p[AttendanceFields.UID])
        assertEquals("2026-08-14", p[AttendanceFields.DATE])
        assertEquals(11, p[AttendanceFields.GRADE])
        assertSame(pembiasaan, p[AttendanceFields.PEMBIASAAN]) // same stamp, not a copy
        assertTrue(p[AttendanceFields.UPDATED_AT] is FieldValue) // server clock sentinel
    }

    // --- checkout ----------------------------------------------------------

    @Test
    fun `checkout payload carries exactly its own keys — no pembiasaan, no grade`() {
        val c = checkoutMergePayload("u1", "2026-08-14", checkout)
        assertEquals(
            setOf(
                AttendanceFields.UID,
                AttendanceFields.DATE,
                AttendanceFields.CHECKOUT,
                AttendanceFields.STATUS,
                AttendanceFields.UPDATED_AT,
            ),
            c.keys,
        )
        assertFalse(c.containsKey(AttendanceFields.PEMBIASAAN))
        assertFalse(c.containsKey(AttendanceFields.GRADE))
    }

    @Test
    fun `checkout payload marks the day complete and passes the stamp through`() {
        val c = checkoutMergePayload("u1", "2026-08-14", checkout)
        assertEquals(AttendanceStatus.COMPLETE, c[AttendanceFields.STATUS])
        assertSame(checkout, c[AttendanceFields.CHECKOUT])
        assertTrue(c[AttendanceFields.UPDATED_AT] is FieldValue)
    }

    @Test
    fun `each call returns an independent map`() {
        val a = pembiasaanMergePayload("u1", "2026-08-14", 11, pembiasaan)
        val b = pembiasaanMergePayload("u1", "2026-08-14", 11, pembiasaan)
        assertEquals(a.keys, b.keys)
        assertFalse("builders must not share a mutable instance", a === b)
    }
}
