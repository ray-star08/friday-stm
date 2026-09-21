@file:Suppress("DEPRECATION")

package com.gynda.fridaystm.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    // Track last recenter request to allow free pan between updates (fix "reset after few seconds").
    var lastCenter by remember { mutableStateOf<GeoPoint?>(null) }
    var lastRecenterKey by remember { mutableIntStateOf(-1) }

    AndroidView(
        modifier = modifier.semantics { contentDescription = description },
        factory = {
            // Prevent parent verticalScroll from stealing map pan gestures (fix restart on drag).
            mapView.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN,
                    android.view.MotionEvent.ACTION_MOVE -> v.parent.requestDisallowInterceptTouchEvent(true)
                    android.view.MotionEvent.ACTION_UP,
                    android.view.MotionEvent.ACTION_CANCEL -> v.parent.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
            mapView
        },
        update = { map ->
            val center = GeoPoint(centerLat, centerLng)
            var needsInvalidate = false

            // Update fence geometry only if the target center or radius changed.
            @Suppress("DEPRECATION")
            val currentPoints = fence.points
            if (currentPoints.isEmpty() || currentPoints[0] != center) {
                @Suppress("DEPRECATION")
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

            // Recenter logic: only animate when center or recenterKey changes (free pan otherwise).
            val anchor = if (recenterKey > 0 && userLat != null && userLng != null) {
                GeoPoint(userLat, userLng)
            } else {
                center
            }
            val shouldRecenter = lastCenter == null || lastCenter != center || lastRecenterKey != recenterKey
            if (shouldRecenter) {
                if (map.mapCenter.latitude != anchor.latitude || map.mapCenter.longitude != anchor.longitude) {
                    map.controller.animateTo(anchor)
                    needsInvalidate = true
                }
                lastCenter = center
                lastRecenterKey = recenterKey
            }

            if (needsInvalidate) {
                map.invalidate()
            }
        },
    )
}

private const val DEFAULT_ZOOM = 17.0
private const val FENCE_STROKE = 4f
