package com.gynda.fridaystm.ui.component

import android.graphics.Color
import android.view.MotionEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.gynda.fridaystm.R
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A small OpenStreetMap (osmdroid) view showing the geofence circle and the live
 * student marker (Milestone 5.1). osmdroid is used over Google Maps: free, no API
 * key and no Play Services dependency.
 *
 * Stateless: it takes only plain lat/lng + radius values (from `HomeUiState`) and
 * draws from them — no ViewModel, no business logic (SKILL.md §4.2).
 *
 * Render fixes vs the previous version:
 * - The [MapView] is created inside `AndroidView(factory)` (app context) and
 *   released via `onRelease { onPause(); onDetach() }`. The old
 *   `remember { MapView(activity) }` + `factory = { sameView }` crashed with
 *   "already has a parent" on recomposition and leaked the Activity.
 * - The osmdroid user-agent is applied synchronously before the first tile
 *   request ([ensureOsmdroidConfigured]); previously it was set in an async
 *   effect after the tiles had already 403'd, leaving a permanent grey grid.
 * - Tile source only switches when the theme actually flips (no flicker), and
 *   dark mode uses real [OsmDark] tiles instead of the harsh invert matrix.
 * - GPS ticks only invalidate when the fix moved > [MOVE_THRESHOLD_M], so
 *   panning no longer jitters every second.
 *
 * @param centerLat/[centerLng] geofence center; the map anchors here.
 * @param radiusMeter geofence radius, drawn as a filled circle.
 * @param userLat/[userLng] live device position; `null` hides the marker.
 * @param recenterKey change this value (e.g. a click counter) to animate the
 *   camera back to the user's position on demand.
 * @param isInside live geofence state for the status pill overlay.
 * @param distanceMeters live distance for the status pill overlay.
 * @param targetLabel fence label for the status pill overlay.
 */
@Suppress("ClickableViewAccessibility") // MapView pan gesture; performClick() called on UP for TalkBack.
@Composable
fun GeofenceMiniMap(
    centerLat: Double,
    centerLng: Double,
    radiusMeter: Int,
    userLat: Double?,
    userLng: Double?,
    modifier: Modifier = Modifier,
    recenterKey: Int = 0,
    isInside: Boolean = false,
    distanceMeters: Double? = null,
    targetLabel: String? = null,
) {
    if (LocalInspectionMode.current) {
        MiniMapPreviewPlaceholder(modifier)
        return
    }

    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val description = stringResource(R.string.cd_minimap)
    val isDark = isSystemInDarkTheme()
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant

    // User-agent synchronously (cheap) so the first tile request is authorised;
    // the heavier prefs load finishes async.
    remember(appContext) { ensureOsmdroidConfigured(appContext); true }
    LaunchedEffect(appContext) { configureOsmdroid(appContext) }

    val dot = remember(appContext, primaryColor) {
        userDotDrawable(appContext, primaryColor.toArgb())
    }

    // Camera bookkeeping: plain state, only written when a move actually happens
    // (initial / fence change / recenter tap / radius-zoom change).
    var isMapInitialCentered by remember { mutableStateOf(false) }
    var lastGeofenceCenter by remember { mutableStateOf<GeoPoint?>(null) }
    var lastRecenterKey by remember { mutableIntStateOf(-1) }
    var lastZoom by remember { mutableStateOf(Double.NaN) }
    var appliedDark by remember { mutableStateOf<Boolean?>(null) }

    val pillText = when {
        userLat == null || userLng == null -> stringResource(R.string.geofence_locating)
        isInside -> stringResource(R.string.geofence_inside, targetLabel.orEmpty())
        distanceMeters != null ->
            stringResource(R.string.geofence_distance, distanceMeters.roundToInt(), targetLabel.orEmpty())
        else -> stringResource(R.string.geofence_locating)
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        border = BorderStroke(1.dp, outlineVariantColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier
                    .matchParentSize()
                    .semantics { contentDescription = description },
                factory = { ctx ->
                    val mapCtx = ctx.applicationContext ?: ctx
                    MapView(mapCtx).apply {
                        applyFridayDefaults()
                        setTileSource(defaultTileSource(isDark))
                        setBackgroundColor(surfaceColor.toArgb())
                        controller.setZoom(zoomForRadius(radiusMeter))
                        controller.setCenter(GeoPoint(centerLat, centerLng))
                        // Prevent parent verticalScroll from stealing map pan gestures.
                        setOnTouchListener { v, event ->
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN,
                                MotionEvent.ACTION_MOVE -> v.parent?.requestDisallowInterceptTouchEvent(true)
                                MotionEvent.ACTION_UP -> {
                                    v.parent?.requestDisallowInterceptTouchEvent(false)
                                    v.performClick()
                                }
                                MotionEvent.ACTION_CANCEL -> v.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                            false
                        }
                        val center = GeoPoint(centerLat, centerLng)
                        val fence = Polygon(this).apply {
                            fillPaint.color = primaryColor.copy(alpha = 0.18f).toArgb()
                            outlinePaint.color = primaryColor.toArgb()
                            outlinePaint.strokeWidth = FENCE_STROKE
                        }
                        @Suppress("DEPRECATION")
                        fence.points = Polygon.pointsAsCircle(center, radiusMeter.toDouble())
                        val halo = Polygon(this).apply {
                            fillPaint.color = primaryColor.copy(alpha = 0.12f).toArgb()
                            outlinePaint.color = Color.TRANSPARENT
                        }
                        val userMarker = Marker(this).apply {
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            icon = dot
                        }
                        tag = MapHolder(
                            fence = fence,
                            halo = halo,
                            marker = userMarker,
                            fenceCenter = center,
                            fenceRadius = radiusMeter,
                            lastUser = null,
                        )
                        overlays.add(fence)
                        onResume()
                    }
                },
                update = { map ->
                    // Theme switch: swap tile source once, never per-frame (no flicker).
                    if (appliedDark != isDark) {
                        try {
                            map.setTileSource(defaultTileSource(isDark))
                        } catch (_: Exception) {
                            // Keep current tiles rather than crashing.
                        }
                        appliedDark = isDark
                    }
                    map.setBackgroundColor(surfaceColor.toArgb())

                    val holder = (map.tag as? MapHolder) ?: run {
                        val center = GeoPoint(centerLat, centerLng)
                        val fence = Polygon(map).apply {
                            fillPaint.color = primaryColor.copy(alpha = 0.18f).toArgb()
                            outlinePaint.color = primaryColor.toArgb()
                            outlinePaint.strokeWidth = FENCE_STROKE
                        }
                        @Suppress("DEPRECATION")
                        fence.points = Polygon.pointsAsCircle(center, radiusMeter.toDouble())
                        val halo = Polygon(map).apply {
                            fillPaint.color = primaryColor.copy(alpha = 0.12f).toArgb()
                            outlinePaint.color = Color.TRANSPARENT
                        }
                        val userMarker = Marker(map).apply {
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        }
                        MapHolder(fence, halo, userMarker, center, radiusMeter, null).also {
                            map.tag = it
                            if (!map.overlays.contains(fence)) map.overlays.add(fence)
                        }
                    }

                    // Refresh paints on theme change.
                    holder.fence.fillPaint.color = primaryColor.copy(alpha = 0.18f).toArgb()
                    holder.fence.outlinePaint.color = primaryColor.toArgb()
                    holder.halo.fillPaint.color = primaryColor.copy(alpha = 0.12f).toArgb()
                    if (holder.marker.icon !== dot) holder.marker.icon = dot

                    var needsInvalidate = false
                    val center = GeoPoint(centerLat, centerLng)

                    // Fence geometry only when center/radius actually changed.
                    if (holder.fenceCenter != center || holder.fenceRadius != radiusMeter) {
                        @Suppress("DEPRECATION")
                        holder.fence.points = Polygon.pointsAsCircle(center, radiusMeter.toDouble())
                        holder.fenceCenter = center
                        holder.fenceRadius = radiusMeter
                        if (!map.overlays.contains(holder.fence)) map.overlays.add(holder.fence)
                        needsInvalidate = true
                    } else if (!map.overlays.contains(holder.fence)) {
                        map.overlays.add(holder.fence)
                        needsInvalidate = true
                    }

                    // User marker + halo, throttled to real movement.
                    val userPoint = if (userLat != null && userLng != null) GeoPoint(userLat, userLng) else null
                    if (userPoint != null) {
                        val prev = holder.lastUser
                        if (prev == null || prev.distanceToAsDouble(userPoint) > MOVE_THRESHOLD_M) {
                            holder.marker.position = userPoint
                            @Suppress("DEPRECATION")
                            holder.halo.points = Polygon.pointsAsCircle(userPoint, USER_HALO_M)
                            holder.lastUser = userPoint
                            needsInvalidate = true
                        }
                        if (!map.overlays.contains(holder.halo)) {
                            // Halo under the marker: insert just before it when possible.
                            val markerIdx = map.overlays.indexOf(holder.marker)
                            if (markerIdx >= 0) map.overlays.add(markerIdx, holder.halo)
                            else map.overlays.add(holder.halo)
                            needsInvalidate = true
                        }
                        if (!map.overlays.contains(holder.marker)) {
                            map.overlays.add(holder.marker)
                            needsInvalidate = true
                        }
                    } else {
                        if (map.overlays.contains(holder.marker)) {
                            map.overlays.remove(holder.marker)
                            needsInvalidate = true
                        }
                        if (map.overlays.contains(holder.halo)) {
                            map.overlays.remove(holder.halo)
                            needsInvalidate = true
                        }
                        holder.lastUser = null
                    }

                    // Camera: initial load, fence change, radius-zoom change, or FAB tap.
                    val zoom = zoomForRadius(radiusMeter)
                    val anchor = if (recenterKey > 0 && userPoint != null) userPoint else center
                    val zoomChanged = lastZoom.isNaN() || abs(lastZoom - zoom) > 0.25
                    val shouldMove = !isMapInitialCentered ||
                        lastGeofenceCenter != center ||
                        lastRecenterKey != recenterKey ||
                        zoomChanged
                    if (shouldMove) {
                        try {
                            map.controller.setZoom(zoom)
                            map.controller.animateTo(anchor)
                        } catch (_: Exception) {
                            // Map mid-teardown; next update retries.
                        }
                        isMapInitialCentered = true
                        lastGeofenceCenter = center
                        lastRecenterKey = recenterKey
                        lastZoom = zoom
                        needsInvalidate = true
                    }

                    if (needsInvalidate) map.invalidate()
                },
                onRelease = { map ->
                    try {
                        map.onPause()
                    } catch (_: Exception) {
                    }
                    try {
                        map.onDetach()
                    } catch (_: Exception) {
                    }
                },
            )

            // Status pill: glanceable inside/outside state drawn over the map.
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                contentColor = if (isInside && userLat != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, outlineVariantColor.copy(alpha = 0.6f)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                ) {
                    if (userLat == null || userLng == null) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp),
                        )
                    } else {
                        Icon(
                            imageVector = if (isInside) Icons.Filled.CheckCircle else Icons.Filled.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(text = pillText, style = MaterialTheme.typography.labelSmall)
                }
            }

            // Licence attribution, always visible.
            Text(
                text = MAP_ATTRIBUTION,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .semantics { contentDescription = MAP_ATTRIBUTION },
            )
        }
    }
}

/** Overlay holder kept in `MapView.tag` so factory-created views survive updates. */
private class MapHolder(
    val fence: Polygon,
    val halo: Polygon,
    val marker: Marker,
    var fenceCenter: GeoPoint?,
    var fenceRadius: Int?,
    var lastUser: GeoPoint?,
)

@Composable
private fun MiniMapPreviewPlaceholder(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocationOn, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Peta", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private const val FENCE_STROKE = 4f

/** Minimum fix movement (m) before the marker/halo is redrawn. */
private const val MOVE_THRESHOLD_M = 3.0

/** Fixed visual halo (m) around the user dot — no accuracy API, anchors the eye. */
private const val USER_HALO_M = 18.0
