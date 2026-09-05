package com.gynda.fridaystm.viewmodel

import com.gynda.fridaystm.data.model.AttendanceRecord
import com.gynda.fridaystm.data.model.CheckoutStamp
import com.gynda.fridaystm.data.model.PembiasaanStamp
import com.gynda.fridaystm.domain.Activity
import com.gynda.fridaystm.domain.FridayPhase
import com.gynda.fridaystm.domain.Role
import com.gynda.fridaystm.util.ActivityType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the role gating added to [resolveHomeAction] (RBAC, task #1).
 *
 * The pure action mapping is the only new non-trivial branch, so it gets a direct
 * self-check rather than being exercised only through the ViewModel.
 */
class HomeActionRoleTest {

    private val larkam = Activity.LARKAM
    private val checkedInLarkam = AttendanceRecord(
        pembiasaan = PembiasaanStamp(activity = ActivityType.LARKAM, checkedIn = true, time = "06:45"),
    )

    @Test
    fun `attending roles are offered the Pembiasaan check-in`() {
        for (role in listOf(Role.STUDENT, Role.CLASS_REP, Role.ADMIN)) {
            assertEquals(
                "role=$role should get a check-in",
                HomeAction.CheckInPembiasaan(larkam),
                resolveHomeAction(FridayPhase.PEMBIASAAN, larkam, record = null, role = role),
            )
        }
    }

    @Test
    fun `instructor is never offered a Pembiasaan check-in`() {
        assertEquals(
            HomeAction.None,
            resolveHomeAction(FridayPhase.PEMBIASAAN, larkam, record = null, role = Role.INSTRUCTOR),
        )
    }

    @Test
    fun `instructor is never offered a check-out`() {
        assertEquals(
            HomeAction.None,
            resolveHomeAction(FridayPhase.CHECKOUT, larkam, record = null, role = Role.INSTRUCTOR),
        )
    }

    @Test
    fun `a recorded check-in still shows Done even for an instructor`() {
        // Defensive: if a stamp somehow exists, reflect reality rather than hiding it.
        assertEquals(
            HomeAction.PembiasaanDone(larkam),
            resolveHomeAction(FridayPhase.PEMBIASAAN, larkam, checkedInLarkam, Role.INSTRUCTOR),
        )
    }

    @Test
    fun `attending role gets CheckOut, and CheckedOut once stamped`() {
        assertEquals(
            HomeAction.CheckOut,
            resolveHomeAction(FridayPhase.CHECKOUT, larkam, record = null, role = Role.STUDENT),
        )
        val doneOut = AttendanceRecord(checkout = CheckoutStamp(checkedOut = true, time = "08:05"))
        assertEquals(
            HomeAction.CheckedOut,
            resolveHomeAction(FridayPhase.CHECKOUT, larkam, doneOut, Role.STUDENT),
        )
    }
}
