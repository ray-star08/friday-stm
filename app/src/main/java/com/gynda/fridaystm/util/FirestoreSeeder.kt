package com.gynda.fridaystm.util

import com.google.firebase.firestore.FirebaseFirestore
import com.gynda.fridaystm.data.model.Geofence
import com.gynda.fridaystm.data.model.RotationSchedule
import com.gynda.fridaystm.data.model.toWireValue
import com.gynda.fridaystm.domain.activityForGrade
import kotlinx.coroutines.tasks.await

/**
 * One-shot seeder for the two **reference** collections the app cannot run
 * without: `geofences` (task.md 2.3) and the `rotations` fallback.
 *
 * Without these, `HomeUiState.Ready.geofenceTarget` is always `null` and the
 * check-in gate can never open — this is the 🔴 blocker in task.md's Kesimpulan.
 *
 * ## How to run it
 * The Firestore rules gate both collections behind `isAdmin()`, so this must be
 * called while signed in as a user whose `users/{uid}.role == "admin"`. It is a
 * **dev/ops tool**, not part of any user flow: call it once from a debug entry
 * point (a temporary button, or `adb shell`-triggered debug Activity), confirm
 * the documents in the console, then stop calling it.
 *
 * Re-running is safe and idempotent: every write targets a deterministic
 * document id with `set()`, so a second run restores the seeded values (and
 * overwrites manual console edits to those same fields — intentional, that is
 * what "restore the baseline" means).
 *
 * ponytail: hard-coded campus coordinates. Fine while there is exactly one
 * school; move to a JSON asset or an admin screen when a second site appears.
 */
suspend fun seedInitialData(
    firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
): Result<Unit> = runCatching {
    val batch = firestore.batch()

    SEED_GEOFENCES.forEach { fence ->
        batch.set(
            firestore.collection(FirestoreCollections.GEOFENCES).document(fence.id),
            fence,
        )
    }
    batch.set(
        firestore.collection(FirestoreCollections.ROTATIONS)
            .document(FirestoreDocIds.ROTATION_FALLBACK),
        FALLBACK_ROTATION,
    )

    // One atomic commit: partial reference data is worse than none.
    batch.commit().await()
    Unit
}

/**
 * The four seeded fences. Document id == [Geofence.activity] wire value, which is
 * also how `resolveGeofenceTarget` finds them (it matches on `activity`).
 *
 * `apel` is seeded for completeness only — the Apel phase was removed from the
 * live flow, so nothing resolves this fence; it keeps legacy documents readable.
 *
 * `internal` so the unit test can assert the payload without touching Firebase.
 */
internal val SEED_GEOFENCES: List<Geofence> = listOf(
    Geofence(
        id = ActivityType.APEL,
        label = "Lapangan Utama",
        activity = ActivityType.APEL,
        lat = -6.87321,
        lng = 107.54223,
        radiusMeter = 50,
    ),
    Geofence(
        id = ActivityType.TALIM,
        label = "Masjid Al-Ikhlas",
        activity = ActivityType.TALIM,
        lat = -6.87350,
        lng = 107.54210,
        radiusMeter = 40,
    ),
    Geofence(
        id = ActivityType.LARKAM,
        label = "Area Lari Kampung",
        activity = ActivityType.LARKAM,
        // Widest fence: a run leaves the start point by design.
        lat = -6.87300,
        lng = 107.54280,
        radiusMeter = 150,
    ),
    Geofence(
        id = ActivityType.SENAM,
        label = "Lapangan Basket",
        activity = ActivityType.SENAM,
        lat = -6.87290,
        lng = 107.54250,
        radiusMeter = 40,
    ),
)

/** Grades covered by the rotation: X, XI, XII. */
private val SEEDED_GRADES = 10..12

/**
 * The `rotations/default_schedule` fallback document.
 *
 * [RotationSchedule.weekOfYear] is [RotationDefaults.FALLBACK_WEEK] — a sentinel
 * meaning "not tied to a week". The mapping is *derived* from the pure
 * `activityForGrade(grade, weekIndex = 0)` rather than typed out, so the seeded
 * document can never drift from the cyclic formula the app actually uses.
 *
 * Declared after its inputs: top-level property initializers run in file order.
 */
internal val FALLBACK_ROTATION: RotationSchedule = RotationSchedule(
    weekOfYear = RotationDefaults.FALLBACK_WEEK,
    mapping = SEEDED_GRADES.associate { grade ->
        grade.toString() to activityForGrade(grade, 0).toWireValue()
    },
)
