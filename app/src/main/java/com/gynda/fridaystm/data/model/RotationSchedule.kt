package com.gynda.fridaystm.data.model

/**
 * Weekly rotation override — Firestore document `rotations/{scheduleId}`.
 *
 * [mapping] maps a grade (as a string key, e.g. `"10"`) to an activity wire
 * value (e.g. `"senam"`). Grade is a string here because Firestore map keys are
 * always strings.
 *
 * This document is an **optional override**: when no rotation exists for a given
 * week, the domain layer falls back to the cyclic formula `activityForGrade`
 * (Milestone 3.2), so the school never has to input a schedule every week.
 *
 * @property weekOfYear ISO week-of-year (1..53) this override applies to.
 */
data class RotationSchedule(
    val weekOfYear: Int = 0,
    val mapping: Map<String, String> = emptyMap(),
)
