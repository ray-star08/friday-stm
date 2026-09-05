package com.gynda.fridaystm.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import com.gynda.fridaystm.util.AttendanceStatus

/**
 * One attendance document per student per day — `attendance/{uid}_{date}`.
 *
 * A single document holds all three Friday phases (apel, pembiasaan, checkout).
 * The deterministic id (see [docIdFor]) makes duplicate check-ins structurally
 * impossible and keeps history / teacher-rekap queries cheap.
 *
 * A `null` stamp means "that phase hasn't happened yet". Writes use
 * `SetOptions.merge()` (see [AttendanceRepository]) so each phase is filled in
 * independently without clobbering the others.
 *
 * @property date ISO date `yyyy-MM-dd`, e.g. `"2026-08-14"`.
 * @property status One of [AttendanceStatus]: incomplete | complete | flagged.
 * @property updatedAt Server clock at the last write. Written as
 *   `FieldValue.serverTimestamp()` and asserted by the Firestore rules
 *   (`request.resource.data.updatedAt == request.time`), so it cannot carry a
 *   tampered device clock. Read-only for the client.
 */
data class AttendanceRecord(
    val uid: String = "",
    val date: String = "",
    val grade: Int = 0,
    val apel: ApelStamp? = null,
    val pembiasaan: PembiasaanStamp? = null,
    val checkout: CheckoutStamp? = null,
    val status: String = AttendanceStatus.INCOMPLETE,
    val updatedAt: Timestamp? = null,
) {
    /**
     * Deterministic document id for this record. Marked [Exclude] so it is never
     * written as a field — it is the document path, not part of the payload.
     */
    @get:Exclude
    val docId: String
        get() = docIdFor(uid, date)

    companion object {
        /** Builds the deterministic id `"${uid}_${date}"`. */
        fun docIdFor(uid: String, date: String): String = "${uid}_$date"
    }
}

/**
 * Fase 1 — Apel Pagi check-in stamp.
 *
 * [serverTime] is populated by Firestore via [ServerTimestamp] on write (leave
 * it `null` when writing and the server fills it in); [time] is the device-
 * formatted `"HH:mm"` kept only for quick display. Per SKILL.md §5 the
 * authoritative time is the server timestamp, never the device clock.
 *
 * `@get:PropertyName` pins the exact wire name on the boolean getters: a Kotlin
 * `Boolean` property whose name starts with `is` would otherwise be serialized
 * with the `is` stripped, and pinning here guards the contract against future
 * renames.
 */
data class ApelStamp(
    @get:PropertyName("checkedIn")
    val checkedIn: Boolean = false,
    val time: String = "",
    @get:ServerTimestamp
    val serverTime: Timestamp? = null,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val selfieUrl: String = "",
    @get:PropertyName("valid")
    val valid: Boolean = false,
)

/**
 * Fase 2 — Pembiasaan check-in stamp. Same shape as [ApelStamp] plus the
 * resolved [activity] (talim | larkam | senam) the student was routed to this
 * week by the rotation logic.
 */
data class PembiasaanStamp(
    val activity: String = "",
    @get:PropertyName("checkedIn")
    val checkedIn: Boolean = false,
    val time: String = "",
    @get:ServerTimestamp
    val serverTime: Timestamp? = null,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val selfieUrl: String = "",
    @get:PropertyName("valid")
    val valid: Boolean = false,
)

/**
 * Fase 3 — Check-out stamp (mandatory before KBM). No geofence/selfie: leaving
 * only records the authoritative time.
 */
data class CheckoutStamp(
    @get:PropertyName("checkedOut")
    val checkedOut: Boolean = false,
    val time: String = "",
    @get:ServerTimestamp
    val serverTime: Timestamp? = null,
)
