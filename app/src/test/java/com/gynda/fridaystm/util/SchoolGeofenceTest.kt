package com.gynda.fridaystm.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class SchoolGeofenceTest {
    @Test
    fun `camera accepts inside and rejects outside 180m around approved demo pin`() {
        for (bearing in listOf(0.0, 90.0, 180.0, 270.0)) {
            val inside = pointFromDemoPin(175.0, bearing)
            val outside = pointFromDemoPin(185.0, bearing)
            assertTrue("175m bearing $bearing should be inside", isWithinSchoolRadius(inside.first, inside.second))
            assertFalse("185m bearing $bearing should be outside", isWithinSchoolRadius(outside.first, outside.second))
        }
    }
}

/** Independent destination calculation anchored to the owner's approved pin, not app constants. */
internal fun pointFromDemoPin(distance: Double, bearingDegrees: Double): Pair<Double, Double> {
    val lat = Math.toRadians(-6.902144277968082)
    val lng = Math.toRadians(107.53840454446247)
    val bearing = Math.toRadians(bearingDegrees)
    val arc = distance / 6_371_000.0
    val destLat = asin(sin(lat) * cos(arc) + cos(lat) * sin(arc) * cos(bearing))
    val destLng = lng + atan2(sin(bearing) * sin(arc) * cos(lat), cos(arc) - sin(lat) * sin(destLat))
    return Math.toDegrees(destLat) to Math.toDegrees(destLng)
}
