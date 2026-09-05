package com.gynda.fridaystm.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.WeekFields

/**
 * Abstraction over the system clock.
 *
 * The Friday phase engine (`resolvePhase`) and the weekly rotation logic
 * (`activityForGrade`) depend on the current time. Per SKILL.md, domain and
 * ViewModel code must **never** call [LocalDateTime.now] directly — time is
 * always injected through this interface so those pure functions can be driven
 * deterministically in unit tests (e.g. asserting the 06:29 → 06:30 transition).
 *
 * Production wiring injects [SystemTimeProvider]; tests inject a fake that
 * returns a fixed instant.
 */
interface TimeProvider {

    /** Current local date-time. */
    fun now(): LocalDateTime

    /** Current local date (derived from [now]). */
    fun today(): LocalDate = now().toLocalDate()

    /** ISO-8601 week of the week-based year (1..53), used by the rotation logic. */
    fun weekOfYear(): Int

    /**
     * ISO week key `yyyy-Www` for [now], e.g. `"2026-W35"` — the id of the current
     * `senam_sessions` document. Derived from the week-based year + [weekOfYear]
     * so it stays consistent with the rotation logic.
     */
    fun weekId(): String {
        val year = now().get(WeekFields.ISO.weekBasedYear())
        return "%d-W%02d".format(year, weekOfYear())
    }
}

/**
 * Default [TimeProvider] backed by the device clock.
 *
 * Pure JVM (`java.time`) — no Android framework references, so it is safe to
 * construct anywhere and keeps this file within the SKILL.md layering rules.
 */
class SystemTimeProvider : TimeProvider {

    override fun now(): LocalDateTime = LocalDateTime.now()

    override fun weekOfYear(): Int =
        now().get(WeekFields.ISO.weekOfWeekBasedYear())
}
