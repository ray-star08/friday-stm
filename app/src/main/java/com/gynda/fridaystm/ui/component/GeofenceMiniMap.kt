package com.gynda.fridaystm.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import com.gynda.fridaystm.R
import com.gynda.fridaystm.ui.theme.PrimaryLight
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

/**
 * A small OpenStreetMap (osmdroid) view showing the geofence circle and the live
 * student marker (Milestone 5.1). osmdroid is used over Google Maps: free, no API
 * key and no Play Services dependency.
 *
 * Stateless: it takes only plain lat/lng + radius values (from `HomeUiState`) and
 * draws from them — no ViewModel, no business logic (SKILL.md §4.2). The osmdroid
 * `MapView` is the sanctioned `AndroidView` wrapper (SKILL.md §1) and its
 * lifecycle (`onResume`/`onPause`/`onDetach`) is driven through a
 * [DisposableEffect] so it never leaks.
 *
 * @param centerLat/[centerLng] geofence center; the map anchors here.
 * @param radiusMeter geofence radius, drawn as a filled circle.
 * @param userLat/[userLng] live device position; `null` hides the marker.
 * @param recenterKey change this value (e.g. a click counter) to animate the
 *   camera back to the user's position on demand.
 */
@Composable
fun GeofenceMiniMap(
    centerLat: Double,
    centerLng: Double,
    radiusMeter: Int,
    userLat: Double?,
    userLng: Double?,
    modifier: Modifier = Modifier,
    recenterKey: Int = 0,
) {
    val context = LocalContext.current
    val description = stringResource(R.string.cd_minimap)

    // One-time osmdroid config (user-agent is mandatory or tile servers 403).
    // Off the main thread — the load touches disk (Startup Stability Plan).
    LaunchedEffect(Unit) { configureOsmdroid(context) }

    // The MapView is the sanctioned AndroidView wrapper.
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(DEFAULT_ZOOM)
        }
    }
    val fence = remember(mapView) {
        Polygon(mapView).apply {
            fillPaint.color = PrimaryLight.copy(alpha = 0.20f).toArgb()
            outlinePaint.color = PrimaryLight.toArgb()
            outlinePaint.strokeWidth = FENCE_STROKE
        }
    }
    val userMarker = remember(mapView) {
        Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
    }

    // Bind osmdroid's manual lifecycle to the composition.
    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    AndroidView(
        modifier = modifier.semantics { contentDescription = description },
        factory = { mapView },
        update = { map ->
            val center = GeoPoint(centerLat, centerLng)
            var needsInvalidate = false

            // Update fence geometry only if the target center or radius changed.
            val currentPoints = fence.points
            if (currentPoints.isEmpty() || currentPoints[0] != center) {
                fence.points = Polygon.pointsAsCircle(center, radiusMeter.toDouble())
                needsInvalidate = true
            }

            // Sync overlays: always ensure fence is present, marker only if fix is live.
            if (!map.overlays.contains(fence)) {
                map.overlays.add(fence)
                needsInvalidate = true
            }

            if (userLat != null && userLng != null) {
                val userPoint = GeoPoint(userLat, userLng)
                if (userMarker.position != userPoint) {
                    userMarker.position = userPoint
                    needsInvalidate = true
                }
                if (!map.overlays.contains(userMarker)) {
                    map.overlays.add(userMarker)
                    needsInvalidate = true
                }
            } else if (map.overlays.contains(userMarker)) {
                map.overlays.remove(userMarker)
                needsInvalidate = true
            }

            // Recenter on the user when asked, else keep the fence centered.
            val anchor = if (recenterKey > 0 && userLat != null && userLng != null) {
                GeoPoint(userLat, userLng)
            } else {
                center
            }
            // Avoid redundant animations if we are already close to the anchor.
            if (map.mapCenter.latitude != anchor.latitude || map.mapCenter.longitude != anchor.longitude) {
                map.controller.animateTo(anchor)
                // animateTo triggers its own updates, but we flag for completeness.
                needsInvalidate = true
            }

            if (needsInvalidate) {
                map.invalidate()
            }
        },
    )
}

private const val DEFAULT_ZOOM = 17.0
private const val FENCE_STROKE = 4f
