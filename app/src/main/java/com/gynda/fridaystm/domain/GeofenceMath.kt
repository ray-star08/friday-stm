package com.gynda.fridaystm.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Mean Earth radius in meters (WGS-84 spherical approximation). */
private const val EARTH_RADIUS_METERS = 6_371_000.0

/**
 * Great-circle distance in meters between two lat/lng points (Haversine).
 *
 * **Purity contract (SKILL.md §6):** pure Kotlin, no `android.location.Location`,
 * no I/O — so the geofence decision is unit-testable on the JVM. The Android
 * location *fetch* lives separately in `util.LocationUtil`; only the coordinates
 * cross into this function.
 *
 * Accurate to well within a meter at campus scale (tens of meters), which is all
 * the geofence radius check needs.
 */
fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
    return EARTH_RADIUS_METERS * (2 * atan2(sqrt(a), sqrt(1 - a)))
}

/**
 * Whether a user position is within [radiusMeter] of a geofence center.
 *
 * The single pure predicate the check-in button gating depends on (Milestone
 * 4.2). Boundary is inclusive: exactly on the radius counts as inside.
 *
 * @param radiusMeter fence radius; non-positive radius means "never inside".
 */
fun isInsideGeofence(
    userLat: Double,
    userLng: Double,
    targetLat: Double,
    targetLng: Double,
    radiusMeter: Int,
): Boolean {
    if (radiusMeter <= 0) return false
    return distanceMeters(userLat, userLng, targetLat, targetLng) <= radiusMeter
}
