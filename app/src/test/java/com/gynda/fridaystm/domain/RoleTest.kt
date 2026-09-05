package com.gynda.fridaystm.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Access-rule coverage for [Role] and [roleFromWire] (RBAC, SKILL.md §6/§9). */
class RoleTest {

    @Test
    fun wireMapping_roundTripsKnownRoles() {
        assertEquals(Role.STUDENT, roleFromWire("student"))
        assertEquals(Role.CLASS_REP, roleFromWire("class_rep"))
        assertEquals(Role.INSTRUCTOR, roleFromWire("instructor"))
        assertEquals(Role.ADMIN, roleFromWire("admin"))
    }

    @Test
    fun unknownOrBlankRole_failsSafeToStudent() {
        assertEquals(Role.STUDENT, roleFromWire(""))
        assertEquals(Role.STUDENT, roleFromWire("teacher")) // legacy value, no longer valid
        assertEquals(Role.STUDENT, roleFromWire("superuser"))
    }

    @Test
    fun onlyClassRepAndAdmin_canSubmitTalimSummary() {
        assertFalse(Role.STUDENT.canSubmitTalimSummary)
        assertFalse(Role.INSTRUCTOR.canSubmitTalimSummary)
        assertTrue(Role.CLASS_REP.canSubmitTalimSummary)
        assertTrue(Role.ADMIN.canSubmitTalimSummary)
    }

    @Test
    fun onlyInstructorAndAdmin_canManageSenam() {
        assertFalse(Role.STUDENT.canManageSenam)
        assertFalse(Role.CLASS_REP.canManageSenam)
        assertTrue(Role.INSTRUCTOR.canManageSenam)
        assertTrue(Role.ADMIN.canManageSenam)
    }

    @Test
    fun instructor_viewsNoSenamButManagesIt() {
        assertFalse(Role.INSTRUCTOR.canViewSenam)
        assertTrue(Role.STUDENT.canViewSenam)
        assertTrue(Role.CLASS_REP.canViewSenam)
    }

    @Test
    fun instructor_doesNotAttendPembiasaan() {
        assertFalse(Role.INSTRUCTOR.canAttendPembiasaan)
        assertTrue(Role.STUDENT.canAttendPembiasaan)
        assertTrue(Role.CLASS_REP.canAttendPembiasaan)
    }
}
