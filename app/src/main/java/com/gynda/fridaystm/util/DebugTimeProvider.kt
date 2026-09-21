package com.gynda.fridaystm.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Debug / Demo [TimeProvider] for presentations without changing the system clock.
 *
 * Wraps a [MutableStateFlow] holding the simulated [LocalDateTime]. The default
 * is the nearest upcoming (or today if already Friday) Friday at 07:00 — i.e.
 * [FridayPhase.PEMBIASAAN] is active immediately so the check-in flow is demoable.
 *
 * Production code sees this as a plain [TimeProvider] via [now]/[weekOfYear]/[weekId].
 * [HomeViewModel] observes [simulatedTime] directly when it detects this subtype,
 * so [resolvePhase] and the weekly rotation re-evaluate instantly on every
 * [setFridayPhase]/[advanceWeek] without waiting for the 30s ticker.
 */
class DebugTimeProvider(
    initial: LocalDateTime = nearestFridayAt(7, 0),
) : TimeProvider {

    private val _simulatedTime = MutableStateFlow(initial)
    val simulatedTime: StateFlow<LocalDateTime> = _simulatedTime.asStateFlow()

    override fun now(): LocalDateTime = _simulatedTime.value

    override fun weekOfYear(): Int =
        _simulatedTime.value.get(WeekFields.ISO.weekOfWeekBasedYear())

    /**
     * Jump to the nearest Friday (today if already Friday) at [hour]:[minute].
     * Keeps the ISO week stable so the rotation demo is isolated from the phase demo.
     */
    fun setFridayPhase(hour: Int, minute: Int) {
        val current = _simulatedTime.value
        val friday = current.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
        _simulatedTime.value = LocalDateTime.of(friday, LocalTime.of(hour, minute))
    }

    /**
     * Advance the simulated date by [weeks] ISO weeks, preserving the time-of-day
     * and the Friday invariant. Used to demo the 3-week rotation cycle
     * (Talim -> Larkam -> Senam) without touching Firestore.
     */
    fun advanceWeek(weeks: Long = 1) {
        _simulatedTime.value = _simulatedTime.value.plusWeeks(weeks)
        // Guarantee still a Friday after the jump (DST-safe: LocalDateTime has no zone).
        val dow = _simulatedTime.value.dayOfWeek
        if (dow != DayOfWeek.FRIDAY) {
            val friday = _simulatedTime.value.toLocalDate()
                .with(TemporalAdjusters.next(DayOfWeek.FRIDAY))
            _simulatedTime.value = LocalDateTime.of(friday, _simulatedTime.value.toLocalTime())
        }
    }

    /** Return to the real device clock — the next [now] reads from [SystemTimeProvider]. */
    fun resetToSystem() {
        _simulatedTime.value = LocalDateTime.now()
        // Snap to the next Friday if we are off-Friday so the demo stays in-phase.
        if (_simulatedTime.value.dayOfWeek != DayOfWeek.FRIDAY) {
            val friday = _simulatedTime.value.toLocalDate()
                .with(TemporalAdjusters.next(DayOfWeek.FRIDAY))
            // Keep the original wall time, just shift the date.
            _simulatedTime.value = LocalDateTime.of(friday, _simulatedTime.value.toLocalTime())
                .withHour(7).withMinute(0).withSecond(0).withNano(0)
        }
    }

    companion object {
        /** Nearest Friday (today if already Friday) at [hour]:[minute]. */
        fun nearestFridayAt(hour: Int, minute: Int): LocalDateTime {
            val today = LocalDate.now()
            val friday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
            return LocalDateTime.of(friday, LocalTime.of(hour, minute))
        }
    }
}
