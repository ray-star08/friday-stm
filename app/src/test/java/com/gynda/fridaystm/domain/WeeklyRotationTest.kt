package com.gynda.fridaystm.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves the weekly rotation is correct and, above all, **clash-free** — the two
 * properties SKILL.md §9 demands: within a week no two grades share an activity,
 * and across 3 weeks each grade cycles through all three activities.
 */
class WeeklyRotationTest {

    private val grades = listOf(10, 11, 12)
    private val allActivities = Activity.entries.toSet()

    // ---- Exact mapping table (weekIndex 0, 1, 2) ------------------------
    // gradeSlot = grade-10; activity = entries[(slot + week) % 3]
    //             week0            week1            week2
    //   10(0):    TALIM            LARKAM           SENAM
    //   11(1):    LARKAM           SENAM            TALIM
    //   12(2):    SENAM            TALIM            LARKAM

    @Test
    fun week0_mapsGradesToDistinctActivities() {
        assertEquals(Activity.TALIM, activityForGrade(10, 0))
        assertEquals(Activity.LARKAM, activityForGrade(11, 0))
        assertEquals(Activity.SENAM, activityForGrade(12, 0))
    }

    @Test
    fun week1_shiftsEveryGradeForwardOneSlot() {
        assertEquals(Activity.LARKAM, activityForGrade(10, 1))
        assertEquals(Activity.SENAM, activityForGrade(11, 1))
        assertEquals(Activity.TALIM, activityForGrade(12, 1))
    }

    @Test
    fun week2_shiftsAgain() {
        assertEquals(Activity.SENAM, activityForGrade(10, 2))
        assertEquals(Activity.TALIM, activityForGrade(11, 2))
        assertEquals(Activity.LARKAM, activityForGrade(12, 2))
    }

    // ---- Property 1: no clash within any single week --------------------

    @Test
    fun everyWeekOfTheYear_hasNoClashBetweenGrades() {
        // Sweep a full ISO year (1..53). Each week the three grades must occupy
        // three DIFFERENT activities.
        for (week in 1..53) {
            val assigned = grades.map { activityForGrade(it, week) }
            assertEquals(
                "Week $week produced a clash: $assigned",
                3,
                assigned.toSet().size,
            )
        }
    }

    // ---- Property 2: each grade sees all 3 activities every 3 weeks ------

    @Test
    fun eachGrade_cyclesThroughAllActivities_overAnyThreeConsecutiveWeeks() {
        for (grade in grades) {
            for (startWeek in 1..53) {
                val overThreeWeeks = (startWeek until startWeek + 3)
                    .map { activityForGrade(grade, it) }
                    .toSet()
                assertEquals(
                    "Grade $grade weeks $startWeek..${startWeek + 2} did not cover all activities",
                    allActivities,
                    overThreeWeeks,
                )
            }
        }
    }

    // ---- Full 3x3 cycle sanity ------------------------------------------

    @Test
    fun fullThreeWeekCycle_isAPerfectLatinSquare() {
        // Rows = weeks 0..2, cols = grades. Every row and every column must be a
        // permutation of the 3 activities (a Latin square) => perfectly balanced.
        for (week in 0..2) {
            val row = grades.map { activityForGrade(it, week) }.toSet()
            assertEquals("Row (week=$week) not balanced", allActivities, row)
        }
        for (grade in grades) {
            val col = (0..2).map { activityForGrade(grade, it) }.toSet()
            assertEquals("Column (grade=$grade) not balanced", allActivities, col)
        }
    }

    // ---- Modular arithmetic: raw week == reduced index ------------------

    @Test
    fun rawWeekOfYear_yieldsSameResultAsReducedIndex() {
        val rawWeeks = listOf(1, 3, 7, 15, 32, 33, 52, 53)
        for (grade in grades) {
            for (week in rawWeeks) {
                assertEquals(
                    "grade=$grade week=$week: raw vs reduced disagree",
                    activityForGrade(grade, rotationWeekIndex(week)),
                    activityForGrade(grade, week),
                )
            }
        }
    }

    @Test
    fun rotationWeekIndex_reducesModuloThree() {
        assertEquals(0, rotationWeekIndex(3))
        assertEquals(1, rotationWeekIndex(1))
        assertEquals(2, rotationWeekIndex(32)) // 32 % 3 == 2
        assertEquals(0, rotationWeekIndex(0))
    }

    // ---- Negative / large inputs handled safely (Kotlin mod) ------------

    @Test
    fun negativeWeekIndex_isHandledWithoutError() {
        // -1 mod 3 == 2, so it must match week index 2.
        for (grade in grades) {
            assertEquals(activityForGrade(grade, 2), activityForGrade(grade, -1))
        }
    }

    // ---- Guard rails -----------------------------------------------------

    @Test
    fun gradeBelowRange_throws() {
        assertThrows(IllegalArgumentException::class.java) { activityForGrade(9, 0) }
    }

    @Test
    fun gradeAboveRange_throws() {
        assertThrows(IllegalArgumentException::class.java) { activityForGrade(13, 0) }
    }

    @Test
    fun everyValidGrade_returnsARotatingActivity() {
        for (grade in grades) {
            assertTrue(activityForGrade(grade, 0) in allActivities)
        }
    }
}
