package com.gynda.fridaystm.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Exhaustive boundary tests for [resolvePhase] (SKILL.md §9 requires coverage of
 * the edges: 06:30, 08:00, 08:30 and non-Friday).
 *
 * All windows are start-inclusive / end-exclusive, so the interesting cases are
 * the exact edges and the last second before each edge. The Apel phase was
 * removed — the flow now goes BEFORE → PEMBIASAAN (at 06:30) → CHECKOUT → DONE.
 */
class PhaseEngineTest {

    // 2026-08-14 is a Friday (verified below); the day before/after bracket it.
    private val friday: LocalDate = LocalDate.of(2026, 8, 14)
    private val thursday: LocalDate = friday.minusDays(1)
    private val saturday: LocalDate = friday.plusDays(1)

    private fun phaseAt(
        hour: Int,
        minute: Int,
        second: Int = 0,
        date: LocalDate = friday,
    ): FridayPhase = resolvePhase(LocalDateTime.of(date, LocalTime.of(hour, minute, second)))

    @Test
    fun testDate_isActuallyFriday() {
        // Guards the whole suite: if the anchor date isn't a Friday the boundary
        // assertions below would silently degrade to NOT_FRIDAY.
        assertEquals(DayOfWeek.FRIDAY, friday.dayOfWeek)
    }

    // ---- BEFORE (< 06:30) ------------------------------------------------

    @Test
    fun midnight_isBefore() {
        assertEquals(FridayPhase.BEFORE, phaseAt(0, 0))
    }

    @Test
    fun at_06_00_isBefore() {
        // 06:00 used to open Apel; with Apel gone it is still the "get ready" window.
        assertEquals(FridayPhase.BEFORE, phaseAt(6, 0))
    }

    @Test
    fun at_06_29_isBefore() {
        assertEquals(FridayPhase.BEFORE, phaseAt(6, 29))
    }

    @Test
    fun oneSecondBefore_06_30_isBefore() {
        assertEquals(FridayPhase.BEFORE, phaseAt(6, 29, 59))
    }

    // ---- PEMBIASAAN (06:30 .. <08:00) -----------------------------------

    @Test
    fun exactly_06_30_isPembiasaan() {
        assertEquals(FridayPhase.PEMBIASAAN, phaseAt(6, 30))
    }

    @Test
    fun midWindow_07_15_isPembiasaan() {
        assertEquals(FridayPhase.PEMBIASAAN, phaseAt(7, 15))
    }

    @Test
    fun oneSecondBefore_08_00_isPembiasaan() {
        assertEquals(FridayPhase.PEMBIASAAN, phaseAt(7, 59, 59))
    }

    // ---- CHECKOUT (08:00 .. <08:30) -------------------------------------

    @Test
    fun exactly_08_00_isCheckout() {
        assertEquals(FridayPhase.CHECKOUT, phaseAt(8, 0))
    }

    @Test
    fun at_08_29_isCheckout() {
        assertEquals(FridayPhase.CHECKOUT, phaseAt(8, 29))
    }

    @Test
    fun oneSecondBefore_08_30_isCheckout() {
        assertEquals(FridayPhase.CHECKOUT, phaseAt(8, 29, 59))
    }

    // ---- DONE (>=08:30) --------------------------------------------------

    @Test
    fun exactly_08_30_isDone() {
        assertEquals(FridayPhase.DONE, phaseAt(8, 30))
    }

    @Test
    fun lateMorning_11_00_isDone() {
        assertEquals(FridayPhase.DONE, phaseAt(11, 0))
    }

    @Test
    fun endOfDay_23_59_isDone() {
        assertEquals(FridayPhase.DONE, phaseAt(23, 59, 59))
    }

    // ---- NOT_FRIDAY (day overrides time) --------------------------------

    @Test
    fun thursdayDuringPembiasaanWindow_isNotFriday() {
        assertEquals(FridayPhase.NOT_FRIDAY, phaseAt(7, 15, date = thursday))
    }

    @Test
    fun saturdayDuringPembiasaanWindow_isNotFriday() {
        assertEquals(FridayPhase.NOT_FRIDAY, phaseAt(7, 15, date = saturday))
    }

    @Test
    fun everyNonFridayDay_isNotFriday_regardlessOfTime() {
        // Walk a full week except Friday; the time sits squarely inside PEMBIASAAN.
        var date = LocalDate.of(2026, 8, 10) // Monday
        repeat(7) {
            if (date.dayOfWeek != DayOfWeek.FRIDAY) {
                assertEquals(
                    "Expected NOT_FRIDAY on ${date.dayOfWeek}",
                    FridayPhase.NOT_FRIDAY,
                    phaseAt(7, 15, date = date),
                )
            }
            date = date.plusDays(1)
        }
    }
}
