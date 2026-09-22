package com.gynda.fridaystm.data.model

import java.time.LocalDateTime

/** Captured run evidence. Repositories snapshot the route before any suspension. */
data class LarkamCapture(
    val distanceKm: Double,
    val durationSeconds: Long,
    val route: List<Map<String, Double>> = emptyList(),
)

/** Owner-bound supplementary evidence; never a canonical check-in or checkout. */
data class CaptureDraft(
    val captureId: String,
    val userId: String,
    val timestamp: LocalDateTime,
    val lat: Double?,
    val lng: Double?,
    val studentName: String,
    val studentClass: String,
    val larkam: LarkamCapture? = null,
)
