package com.gynda.fridaystm.domain

/**
 * The state machine that drives the whole Friday-morning UI.
 *
 * A single [FridayPhase] value, resolved purely from the current time by
 * [resolvePhase], decides which button/card the [HomeScreen] shows. This enum is
 * the exhaustive set of states — the UI must handle every case (SKILL.md §3.2).
 *
 * The legacy Apel phase was removed: the Friday flow now opens directly into
 * Pembiasaan at 06:30. Historical `attendance.apel` documents are still readable
 * (see `AttendanceRecord.apel`), but no phase produces new Apel check-ins.
 */
enum class FridayPhase {
    /** Not a Friday at all — the app is idle (empty state). */
    NOT_FRIDAY,

    /** Friday, but before Pembiasaan opens (< 06:30) — "get ready" state. */
    BEFORE,

    /** 06:30–08:00 — Pembiasaan (Larkam / Ta'lim / Senam by weekly rotation) is open. */
    PEMBIASAAN,

    /** 08:00–08:30 — mandatory check-out window before KBM. */
    CHECKOUT,

    /** After the check-out window closed — the Friday flow is finished. */
    DONE,
}
