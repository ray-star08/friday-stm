package com.gynda.fridaystm.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure geofence math (SKILL.md §6, §9). No Android
 * `Location` — coordinates in, boolean/distance out — so the check-in gating
 * rule is verified deterministically on the JVM.
 */
class GeofenceMathTest {

    // Campus reference point (task.md example).
    private val centerLat = -6.87
    private val centerLng = 107.54

    @Test
    fun distance_isZero_forSamePoint() {
        assertEquals(0.0, distanceMeters(centerLat, centerLng, centerLat, centerLng), 0.01)
    }

    @Test
    fun distance_matchesKnownSeparation_withinTolerance() {
        // 0.001° of latitude ≈ 111.19 m near the equator (independent of longitude).
        val d = distanceMeters(centerLat, centerLng, centerLat + 0.001, centerLng)
        assertEquals(111.19, d, 0.5)
    }

    @Test
    fun inside_whenExactlyAtCenter() {
        assertTrue(isInsideGeofence(centerLat, centerLng, centerLat, centerLng, radiusMeter = 40))
    }

    @Test
    fun inside_whenWithinRadius() {
        // ~11 m north of center, well inside a 40 m fence.
        assertTrue(isInsideGeofence(centerLat + 0.0001, centerLng, centerLat, centerLng, 40))
    }

    @Test
    fun outside_whenBeyondRadius() {
        // ~111 m north of center, outside a 40 m fence.
        assertFalse(isInsideGeofence(centerLat + 0.001, centerLng, centerLat, centerLng, 40))
    }

    @Test
    fun boundary_isInclusive() {
        // Pick a radius equal to the computed distance → on-the-edge counts as inside.
        val d = distanceMeters(centerLat, centerLng, centerLat + 0.0002, centerLng)
        assertTrue(isInsideGeofence(centerLat + 0.0002, centerLng, centerLat, centerLng, radiusMeter = kotlin.math.ceil(d).toInt()))
    }

    @Test
    fun nonPositiveRadius_isNeverInside() {
        assertFalse(isInsideGeofence(centerLat, centerLng, centerLat, centerLng, radiusMeter = 0))
        assertFalse(isInsideGeofence(centerLat, centerLng, centerLat, centerLng, radiusMeter = -5))
    }
}
