package com.gynda.fridaystm.util

import android.location.Location
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SchoolGeofenceInstrumentedTest {
    @Test
    fun realAndroidDistanceUsesApprovedPinAndKeeps180mBoundary() {
        // Native Location.distanceBetween, not the JVM Haversine fallback.
        val center = Location("synthetic-test").apply {
            latitude = -6.902144277968082
            longitude = 107.53840454446247
        }
        assertEquals(0.0f, calculateDistanceToSchool(center.latitude, center.longitude), 0.1f)
        assertEquals(180.0f, MAX_RADIUS_METERS, 0.0f)
        val directions = listOf(1.0 to 0.0, 0.0 to 1.0, -1.0 to 0.0, 0.0 to -1.0)
        directions.forEach { (north, east) ->
            val inner = Location("synthetic-test").apply {
                latitude = center.latitude + north * 0.0015
                longitude = center.longitude + east * 0.0015
            }
            val outer = Location("synthetic-test").apply {
                latitude = center.latitude + north * 0.0018
                longitude = center.longitude + east * 0.0018
            }
            assertTrue(center.distanceTo(inner) < 180.0f)
            assertTrue(center.distanceTo(outer) > 180.0f)
            assertTrue(isWithinSchoolRadius(inner.latitude, inner.longitude))
            assertFalse(isWithinSchoolRadius(outer.latitude, outer.longitude))
        }
    }
}
