package com.gynda.fridaystm.data.model

import com.gynda.fridaystm.util.ActivityType
import com.gynda.fridaystm.util.GeofenceDefaults

/**
 * A digital fence ("pagar digital") — Firestore document `geofences/{id}`.
 *
 * Reference data: seeded once (Milestone 2.3) and read-only for students. The
 * pure geofence math (`isInsideGeofence`) that consumes [lat]/[lng]/[radiusMeter]
 * lives in `domain/` (Milestone 4.2) and never imports this class directly.
 *
 * @property activity One of [ActivityType]: apel | talim | larkam | senam.
 * @property radiusMeter Fence radius in meters; compared against
 *   `Location.distanceBetween(...)` at check-in.
 */
data class Geofence(
    val id: String = "",
    val label: String = "",
    val activity: String = ActivityType.APEL,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val radiusMeter: Int = GeofenceDefaults.RADIUS_METER,
)
