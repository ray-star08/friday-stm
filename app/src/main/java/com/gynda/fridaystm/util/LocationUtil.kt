package com.gynda.fridaystm.util

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.gynda.fridaystm.domain.isInsideGeofence
import kotlinx.coroutines.tasks.await

/**
 * A fixed position reading used for geofence validation.
 *
 * @property isMock the OS flagged this fix as mock-provided — a fake-GPS signal
 *   the ViewModel can treat as an invalid check-in (SKILL.md §8, task 4.2).
 */
data class LocationFix(
    val lat: Double,
    val lng: Double,
    val isMock: Boolean,
)

/**
 * Thin coroutine wrapper over [com.google.android.gms.location.FusedLocationProviderClient].
 *
 * Lives in `util/` because it touches the Android framework; the ViewModel never
 * calls the fused client directly (SKILL.md §3.2) — it depends on this interface,
 * so tests inject a fake. The *decision* (inside/outside the fence) stays in the
 * pure `domain.isInsideGeofence`; this type only fetches coordinates.
 */
interface LocationProvider {

    /**
     * One high-accuracy fix. Caller must already hold `ACCESS_FINE_LOCATION`
     * (requested at the UI boundary, task 4.1).
     *
     * @return [Result.failure] if location is unavailable (e.g. GPS off, no fix).
     */
    suspend fun currentLocation(): Result<LocationFix>
}

/** Play-Services-backed [LocationProvider]. */
class FusedLocationProvider(context: Context) : LocationProvider {

    private val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)

    @SuppressLint("MissingPermission") // caller gates on the runtime permission (task 4.1)
    override suspend fun currentLocation(): Result<LocationFix> = runCatching {
        val location = client
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .await()
            ?: error("No location fix available")
        LocationFix(
            lat = location.latitude,
            lng = location.longitude,
            isMock = location.isFromMockProviderCompat(),
        )
    }
}

/** `isFromMockProvider` was deprecated for `isMock` at API 31; pick per level. */
private fun android.location.Location.isFromMockProviderCompat(): Boolean =
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) isMock
    else @Suppress("DEPRECATION") isFromMockProvider

/**
 * Convenience: fetch the current fix and test it against a target fence in one
 * call. Returns `false` on any location failure or a mock-provided fix.
 */
suspend fun LocationProvider.isWithin(
    targetLat: Double,
    targetLng: Double,
    radiusMeter: Int,
): Boolean = currentLocation().fold(
    onSuccess = { fix ->
        !fix.isMock && isInsideGeofence(fix.lat, fix.lng, targetLat, targetLng, radiusMeter)
    },
    onFailure = { false },
)
