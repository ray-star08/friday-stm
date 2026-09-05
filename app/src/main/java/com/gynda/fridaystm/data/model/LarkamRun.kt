package com.gynda.fridaystm.data.model

/**
 * A completed Larkam run, as persisted to Firestore `larkam_runs` (Activity 4).
 *
 * Pure local shape (no Firestore annotations): the repository maps this to the
 * wire document and stamps `createdAt` server-side. [path] is already in the
 * stored form — a list of `{ "lat": …, "lng": … }` points — so the repo write is
 * a straight passthrough.
 *
 * @property userId uid of the student who ran.
 * @property distanceMeters accumulated haversine distance.
 * @property elapsedSec whole seconds of the session.
 * @property path ordered GPS trail, each point `{ "lat": x, "lng": y }`.
 */
data class LarkamRun(
    val userId: String,
    val distanceMeters: Double,
    val elapsedSec: Long,
    val path: List<Map<String, Double>>,
)
