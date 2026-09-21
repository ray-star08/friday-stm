package com.gynda.fridaystm.util

import android.location.Location
import com.gynda.fridaystm.domain.distanceMeters

/**
 * Geofencing helpers for presensi selfie — validates if user is within school radius.
 * Uses [Location.distanceBetween] with haversine fallback for JVM unit tests.
 */

fun calculateDistanceToSchool(lat: Double, lng: Double): Float {
    return try {
        val res = FloatArray(1)
        Location.distanceBetween(lat, lng, SCHOOL_LATITUDE, SCHOOL_LONGITUDE, res)
        val d = res[0]
        // On JVM unit test, distanceBetween returns 0 (mocked) — fallback to haversine
        if (d == 0f) distanceMeters(lat, lng, SCHOOL_LATITUDE, SCHOOL_LONGITUDE).toFloat() else d
    } catch (_: Exception) {
        distanceMeters(lat, lng, SCHOOL_LATITUDE, SCHOOL_LONGITUDE).toFloat()
    }
}

fun isWithinSchoolRadius(lat: Double, lng: Double): Boolean {
    return calculateDistanceToSchool(lat, lng) <= MAX_RADIUS_METERS
}
