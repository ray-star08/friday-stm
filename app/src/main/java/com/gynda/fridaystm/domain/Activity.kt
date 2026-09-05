package com.gynda.fridaystm.domain

/**
 * The three "pembiasaan" activities that rotate across grades each Friday
 * (the 06:30–08:00 [FridayPhase.PEMBIASAAN] window).
 *
 * **Declaration order is significant:** [activityForGrade] indexes into
 * [Activity.entries], so `TALIM=0, LARKAM=1, SENAM=2`. Do not reorder without
 * updating the rotation tests.
 *
 * This is a pure domain type — it carries no Firestore wire string. Mapping
 * to/from the persisted `activity` string (`util.ActivityType`) is the data
 * layer's job, done in the repository (SKILL.md §5), keeping `domain/` free of
 * any serialization concern.
 */
enum class Activity {
    TALIM,
    LARKAM,
    SENAM,
}
