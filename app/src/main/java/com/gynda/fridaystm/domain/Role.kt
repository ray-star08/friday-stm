package com.gynda.fridaystm.domain

/**
 * Role-based access control, expressed as pure domain logic (SKILL.md §6) so the
 * UI-gating decisions are unit-testable with zero Android/Firebase.
 *
 * The persisted `users/{uid}.role` string ([com.gynda.fridaystm.util.UserRole])
 * maps into this enum via [roleFromWire]; an unknown/blank value degrades to
 * [STUDENT] (fail-safe: the least-privileged role), never a crash.
 */
enum class Role {
    STUDENT,
    CLASS_REP,
    INSTRUCTOR,
    ADMIN,
    ;

    /** May check in to Larkam / Ta'lim / Senam (all non-instructor students). */
    val canAttendPembiasaan: Boolean get() = this == STUDENT || this == CLASS_REP || this == ADMIN

    /** May submit the Ta'lim class summary form (class reps + admin override). */
    val canSubmitTalimSummary: Boolean get() = this == CLASS_REP || this == ADMIN

    /** May set the weekly Senam video (instructors + admin override). */
    val canManageSenam: Boolean get() = this == INSTRUCTOR || this == ADMIN

    /** May watch the weekly Senam video on the dashboard (everyone except instructor). */
    val canViewSenam: Boolean get() = this != INSTRUCTOR
}

/**
 * Parses a persisted `role` wire string to a [Role]. Unknown/blank ⇒ [Role.STUDENT]
 * (fail-safe least privilege). Kept out of the data layer's constants so the
 * mapping table lives with the access rules it feeds.
 */
fun roleFromWire(value: String): Role = when (value) {
    "student" -> Role.STUDENT
    "class_rep" -> Role.CLASS_REP
    "instructor" -> Role.INSTRUCTOR
    "admin" -> Role.ADMIN
    else -> Role.STUDENT
}
