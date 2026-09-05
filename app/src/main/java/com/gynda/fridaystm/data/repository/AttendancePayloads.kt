package com.gynda.fridaystm.data.repository

import com.google.firebase.firestore.FieldValue
import com.gynda.fridaystm.data.model.CheckoutStamp
import com.gynda.fridaystm.data.model.PembiasaanStamp
import com.gynda.fridaystm.util.AttendanceFields
import com.gynda.fridaystm.util.AttendanceStatus

/**
 * Pure builders for the `SetOptions.merge()` field maps written to
 * `attendance/{uid}_{date}` by [FirestoreAttendanceRepository] (task 3.3).
 *
 * Extracted from the repository so the **merge-isolation** contract — a
 * single-phase write touches only its own keys and never nulls a sibling phase —
 * is unit-testable on the JVM without Firestore. The only Firebase type used is
 * the [FieldValue.serverTimestamp] sentinel, which needs no `FirebaseApp`.
 *
 * A field map (not the full POJO) is deliberate: merging the whole
 * [com.gynda.fridaystm.data.model.AttendanceRecord] would write its `null`
 * siblings and clobber the other phases.
 */

/** Merge payload for a Fase 2 (Pembiasaan) check-in — only pembiasaan + metadata. */
fun pembiasaanMergePayload(
    uid: String,
    date: String,
    grade: Int,
    stamp: PembiasaanStamp,
): Map<String, Any> = mapOf(
    AttendanceFields.UID to uid,
    AttendanceFields.DATE to date,
    AttendanceFields.GRADE to grade,
    AttendanceFields.PEMBIASAAN to stamp,
    // Server clock, not the device's — the rules require it to equal request.time.
    AttendanceFields.UPDATED_AT to FieldValue.serverTimestamp(),
)

/** Merge payload for a Fase 3 (Check-out) — only checkout + marks the day complete. */
fun checkoutMergePayload(
    uid: String,
    date: String,
    stamp: CheckoutStamp,
): Map<String, Any> = mapOf(
    AttendanceFields.UID to uid,
    AttendanceFields.DATE to date,
    AttendanceFields.CHECKOUT to stamp,
    AttendanceFields.STATUS to AttendanceStatus.COMPLETE,
    AttendanceFields.UPDATED_AT to FieldValue.serverTimestamp(),
)
