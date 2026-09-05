package com.gynda.fridaystm.domain

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Time boundaries of the Friday-morning flow. Centralized here (no magic times
 * scattered in code, SKILL.md §7) and expressed with pure `java.time` types.
 *
 * Windows are **start-inclusive, end-exclusive** — e.g. 06:30:00 is already
 * [FridayPhase.PEMBIASAAN] while 08:00:00 has already flipped to
 * [FridayPhase.CHECKOUT].
 */
object PhaseSchedule {

    /** The one day of the week the whole app is active. */
    val ACTIVITY_DAY: DayOfWeek = DayOfWeek.FRIDAY

    /** 06:30 — the "get ready" window ends and Pembiasaan opens. */
    val PEMBIASAAN_START: LocalTime = LocalTime.of(6, 30)

    /** 08:00 — Pembiasaan closes, Check-out opens. */
    val CHECKOUT_START: LocalTime = LocalTime.of(8, 0)

    /** 08:30 — Check-out window closes; the flow is DONE. */
    val CHECKOUT_END: LocalTime = LocalTime.of(8, 30)
}

/**
 * Pure function at the heart of the app: maps an instant to a [FridayPhase].
 *
 * **Purity contract (SKILL.md §6):** this function reads *nothing* — no clock,
 * no Android, no I/O. The instant is passed in as [now]; production code obtains
 * it from `TimeProvider` at the ViewModel boundary and forwards it here, which is
 * exactly what makes the 06:29 → 06:30 transition deterministically testable.
 *
 * Boundaries (start-inclusive, end-exclusive), see [PhaseSchedule]:
 * ```
 * not Friday        -> NOT_FRIDAY
 *          < 06:30  -> BEFORE
 * 06:30 .. < 08:00  -> PEMBIASAAN
 * 08:00 .. < 08:30  -> CHECKOUT
 *         >= 08:30  -> DONE
 * ```
 *
 * @param now the local date-time to classify (injected, never read internally).
 * @return the phase the UI should render for [now].
 */
fun resolvePhase(now: LocalDateTime): FridayPhase {
    if (now.dayOfWeek != PhaseSchedule.ACTIVITY_DAY) return FridayPhase.NOT_FRIDAY

    val time = now.toLocalTime()
    return when {
        time < PhaseSchedule.PEMBIASAAN_START -> FridayPhase.BEFORE
        time < PhaseSchedule.CHECKOUT_START -> FridayPhase.PEMBIASAAN
        time < PhaseSchedule.CHECKOUT_END -> FridayPhase.CHECKOUT
        else -> FridayPhase.DONE
    }
}
