package com.gynda.fridaystm.util

/**
 * Single source of truth for Firestore collection names, wire-level string
 * values, asset paths and geofence defaults.
 *
 * Per SKILL.md §7, collection/field names and any "magic" strings/numbers must
 * live here and never be typed inline at call sites. The `data/model` classes
 * reference these as default values so the wire contract has exactly one home.
 */

/** Firestore top-level collection names. */
object FirestoreCollections {
    const val USERS = "users"
    const val GEOFENCES = "geofences"
    const val ROTATIONS = "rotations"
    const val ATTENDANCE = "attendance"

    /** Ta'lim class summaries — doc id `{date}_{kelas}` (class-rep only). */
    const val TALIM_SUMMARIES = "talim_summaries"

    /** Weekly Senam video sessions — doc id `{weekId}` (instructor-managed). */
    const val SENAM_SESSIONS = "senam_sessions"

    /** Larkam run logs, appended per session (Firestore-only, no external sync). */
    const val LARKAM_RUNS = "larkam_runs"
}

/**
 * Well-known document ids that are **not** derived from a uid/date/week.
 *
 * Deterministic ids ([AttendanceRecord.docIdFor] and friends) are built at the
 * call site; only fixed, singleton documents belong here.
 */
object FirestoreDocIds {
    /**
     * The rotation fallback document `rotations/default_schedule` — the mapping
     * used when no week-specific override exists (see `FirestoreSeeder`).
     */
    const val ROTATION_FALLBACK = "default_schedule"
}

/** Sentinel values that carry meaning on the wire. */
object RotationDefaults {
    /**
     * `rotations/{id}.weekOfYear == 0` marks the **week-agnostic fallback**
     * document. ISO weeks are 1..53, so 0 can never collide with a real week.
     *
     * The effective schedule is resolved by **document id**, in this order:
     * `rotations/{weekId}` (e.g. `2026-W40`, created by an admin for a holiday or
     * a special week) → [FirestoreDocIds.ROTATION_FALLBACK] → the pure cyclic
     * formula `activityForGrade`. No query, no index: two document reads, both
     * served from the Firestore offline cache once warm.
     */
    const val FALLBACK_WEEK = 0
}

/** Allowed values for `users/{uid}.role`. Mirrors [com.gynda.fridaystm.domain.Role]. */
object UserRole {
    const val STUDENT = "student"
    const val CLASS_REP = "class_rep"
    const val INSTRUCTOR = "instructor"
    const val ADMIN = "admin"
}

/**
 * Wire values for a Friday activity.
 *
 * Stored as plain strings on the wire (`geofences.activity`, rotation `mapping`
 * values, `attendance.pembiasaan.activity`) so Firestore deserialization can
 * never crash on an unknown value. The typed `Activity` domain enum (Milestone
 * 3) maps to/from these constants — the data layer stays a dumb, crash-free DTO.
 */
object ActivityType {
    const val APEL = "apel"
    const val TALIM = "talim"
    const val LARKAM = "larkam"
    const val SENAM = "senam"

    /** The three rotating "pembiasaan" activities (excludes apel). */
    val PEMBIASAAN: List<String> = listOf(TALIM, LARKAM, SENAM)
}

/** Values for `attendance.status`. */
object AttendanceStatus {
    const val INCOMPLETE = "incomplete"
    const val COMPLETE = "complete"
    const val FLAGGED = "flagged"
}

/**
 * Top-level field names inside an `attendance/{uid_date}` document.
 *
 * The repository writes each phase with `SetOptions.merge()` using these keys as
 * field paths, so one phase never clobbers another. Centralized here per
 * SKILL.md §7 — no raw field strings at call sites.
 */
object AttendanceFields {
    const val UID = "uid"
    const val DATE = "date"
    const val GRADE = "grade"
    const val APEL = "apel"
    const val PEMBIASAAN = "pembiasaan"
    const val CHECKOUT = "checkout"
    const val STATUS = "status"

    /**
     * Server-authored write marker. Every write sets this to
     * `FieldValue.serverTimestamp()`; the Firestore rules assert
     * `request.resource.data.updatedAt == request.time`, so a client that
     * substitutes its own (tampered) device clock is rejected outright.
     */
    const val UPDATED_AT = "updatedAt"
}

/** Phase tags used when composing selfie file names. */
object SelfiePhase {
    const val APEL = "apel"
    const val PEMBIASAAN = "pembiasaan"
}

/** Geofence defaults. */
object GeofenceDefaults {
    /** Fallback radius (meters) when a geofence document omits `radiusMeter`. */
    const val RADIUS_METER = 40
}

/**
 * Cloudinary unsigned-upload layout (M4.3). The storage provider is Cloudinary,
 * not Firebase Storage (SKILL policy) — `cloudName`/`uploadPreset` come from
 * `BuildConfig` (injected at build time from `local.properties`), never inlined.
 */
object CloudinaryConfig {
    /** Unsigned image upload endpoint for a given cloud name. */
    fun uploadUrl(cloudName: String): String =
        "https://api.cloudinary.com/v1_1/$cloudName/image/upload"

    /** Asset folder per student: `selfies/{uid}`. */
    fun folder(uid: String): String = "selfies/$uid"

    /**
     * Deterministic public id (without extension) inside the folder: one asset
     * per phase per day, e.g. `2026-08-14_apel`. Overwriting requires the upload
     * preset to allow it; otherwise Cloudinary appends a suffix.
     */
    fun publicId(date: String, phase: String): String = "${date}_$phase"
}

