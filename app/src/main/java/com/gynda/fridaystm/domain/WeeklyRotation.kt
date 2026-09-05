package com.gynda.fridaystm.domain

/** Lowest supported grade; maps to rotation slot 0. */
private const val LOWEST_GRADE = 10

/** Supported grades: 10, 11, 12. */
private val GRADE_RANGE = LOWEST_GRADE..(LOWEST_GRADE + Activity.entries.size - 1)

/** Size of the cycle — 3 grades, 3 activities, 3-week period. */
private val ROTATION_SIZE = Activity.entries.size

/**
 * Reduces a raw ISO week-of-year to its position in the 3-week cycle (0, 1, 2).
 *
 * Purely a convenience for readable call sites; passing the raw week straight to
 * [activityForGrade] yields the same result (see that function's note on modular
 * arithmetic).
 *
 * @param weekOfYear ISO-8601 week of the week-based year (any integer).
 */
fun rotationWeekIndex(weekOfYear: Int): Int = weekOfYear.mod(ROTATION_SIZE)

/**
 * Pure function deciding which pembiasaan [Activity] a grade does in a given week.
 *
 * ### The math (modulo cycle)
 * Each grade owns a fixed *slot*: `gradeSlot = grade - 10`, so
 * `10 → 0, 11 → 1, 12 → 2`. The activity is then:
 *
 * ```
 * activityIndex = (gradeSlot + weekIndex) mod 3
 * activity      = Activity.entries[activityIndex]   // 0=TALIM, 1=LARKAM, 2=SENAM
 * ```
 *
 * Adding `weekIndex` and taking `mod 3` rotates every grade forward by one slot
 * each week. Two guarantees fall straight out of modular arithmetic:
 * - **No clash in a week:** for a fixed `weekIndex`, `slot ↦ (slot + weekIndex) mod 3`
 *   is a bijection on `{0,1,2}`, so the three grades always land on three
 *   *different* activities.
 * - **Fair over 3 weeks:** for a fixed grade, `weekIndex ↦ (slot + weekIndex) mod 3`
 *   is also a bijection, so across any 3 consecutive weeks the grade does each
 *   activity exactly once.
 *
 * [weekIndex] may be the **raw** ISO week-of-year or a value already reduced by
 * [rotationWeekIndex] — both give the same answer, because
 * `(slot + w) mod 3 == (slot + (w mod 3)) mod 3`. Negative inputs are handled
 * safely via Kotlin's `mod` (always non-negative).
 *
 * @param grade student grade, must be in 10..12.
 * @param weekIndex week position (raw ISO week or reduced index).
 * @return the activity this grade is routed to that week.
 * @throws IllegalArgumentException if [grade] is outside 10..12.
 */
fun activityForGrade(grade: Int, weekIndex: Int): Activity {
    require(grade in GRADE_RANGE) {
        "grade must be in $GRADE_RANGE (10=X, 11=XI, 12=XII), was $grade"
    }
    val gradeSlot = grade - LOWEST_GRADE
    val activityIndex = (gradeSlot + weekIndex).mod(ROTATION_SIZE)
    return Activity.entries[activityIndex]
}
