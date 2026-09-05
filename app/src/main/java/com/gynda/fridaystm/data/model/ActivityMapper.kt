package com.gynda.fridaystm.data.model

import com.gynda.fridaystm.domain.Activity
import com.gynda.fridaystm.util.ActivityType

/**
 * Mapping between the pure [Activity] domain enum and its Firestore wire string
 * ([ActivityType]).
 *
 * This lives in the data layer on purpose: `domain/Activity` deliberately carries
 * no serialization knowledge (see its KDoc), so the translation to/from the
 * persisted `"talim" | "larkam" | "senam"` values is done here — keeping the
 * domain a crash-free, wire-agnostic type (SKILL.md §5, §6).
 */

/** Wire value written to `attendance.pembiasaan.activity` for this activity. */
fun Activity.toWireValue(): String = when (this) {
    Activity.TALIM -> ActivityType.TALIM
    Activity.LARKAM -> ActivityType.LARKAM
    Activity.SENAM -> ActivityType.SENAM
}

/**
 * Parses a persisted wire value back to an [Activity].
 *
 * Returns `null` for anything that is not one of the three rotating pembiasaan
 * activities (including `"apel"`), so a corrupt/unknown value can never crash
 * deserialization — the caller decides how to treat the absence.
 */
fun activityFromWire(value: String): Activity? = when (value) {
    ActivityType.TALIM -> Activity.TALIM
    ActivityType.LARKAM -> Activity.LARKAM
    ActivityType.SENAM -> Activity.SENAM
    else -> null
}
