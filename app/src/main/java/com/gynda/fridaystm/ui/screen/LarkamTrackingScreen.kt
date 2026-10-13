package com.gynda.fridaystm.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gynda.fridaystm.R
import com.gynda.fridaystm.ui.component.MAP_ATTRIBUTION
import com.gynda.fridaystm.ui.component.applyFridayDefaults
import com.gynda.fridaystm.ui.component.defaultTileSource
import com.gynda.fridaystm.ui.component.ensureOsmdroidConfigured
import com.gynda.fridaystm.ui.component.userDotDrawable
import com.gynda.fridaystm.ui.theme.FridaySTMTheme
import com.gynda.fridaystm.ui.theme.PrimaryLight
import com.gynda.fridaystm.viewmodel.LarkamUiState
import com.gynda.fridaystm.viewmodel.LarkamViewModel
import com.gynda.fridaystm.viewmodel.RunPoint
import com.gynda.fridaystm.viewmodel.RunStatus
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * Larkam — stateful holder. Owns the [LarkamViewModel], renders the live run
 * (map + duration/distance + pace) and controls Mulai/Jeda/Selesai & Selfie.
 * On a saved run it shows a Toast and navigates via [onDone].
 * For spec: "Selesai & Selfie" navigates to PresensiCameraScreen with payload,
 * the ViewModel's watermark will be applied there.
 */
@Composable
fun LarkamTrackingScreen(
    viewModel: LarkamViewModel,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onFinishSelfie: (() -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val saveSuccessMessage = stringResource(R.string.larkam_save_success)
    LaunchedEffect(state.status) {
        if (state.status == RunStatus.Saved) {
            Toast.makeText(context, saveSuccessMessage, Toast.LENGTH_SHORT).show()
        }
    }
    LarkamContent(
        state = state,
        onStart = viewModel::onStart,
        onPause = viewModel::onPause,
        onResume = viewModel::onResume,
        onStop = viewModel::onStop,
        onFinish = {
            viewModel.onFinish()
            if (viewModel.captureIntent() != null) onFinishSelfie?.invoke()
        },
        onFinishSelfie = onFinishSelfie,
        onDone = onDone,
        modifier = modifier,
    )
}

/** Larkam — stateless content, previewable from [state] alone. */
@Composable
private fun LarkamContent(
    state: LarkamUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    onFinish: () -> Unit = {},
    onFinishSelfie: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text(
            text = stringResource(R.string.larkam_track_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(16.dp))

        RunPathMap(
            path = state.path,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        Spacer(Modifier.height(16.dp))

        // Live dashboard: Jarak (KM 2 desimal), Timer (HH:MM:SS), Pace
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stat(label = stringResource(R.string.larkam_duration), value = formatElapsed(state.elapsedSec))
            Stat(label = stringResource(R.string.larkam_distance), value = formatDistance(state.distanceMeters))
            Stat(label = "Pace", value = formatPace(state))
        }
        Spacer(Modifier.height(16.dp))

        when (val status = state.status) {
            RunStatus.Idle -> {
                CenterHint(stringResource(R.string.larkam_hint))
                Spacer(Modifier.height(12.dp))
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.larkam_start))
                }
            }

            RunStatus.Running -> Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onPause, modifier = Modifier.fillMaxWidth()) {
                    Text("Jeda")
                }
                Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                    Text("Selesai & Selfie")
                }
            }

            RunStatus.Paused -> Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CenterHint("Jeda — tap Lanjutkan untuk melanjutkan")
                Button(onClick = onResume, modifier = Modifier.fillMaxWidth()) {
                    Text("Lanjutkan")
                }
                Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                    Text("Selesai & Selfie")
                }
            }

            RunStatus.Finished -> Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CenterHint("Selesai — siap selfie watermark")
                Button(onClick = { onFinishSelfie?.invoke() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Selfie Sekarang")
                }
                OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                    Text("Simpan Tanpa Selfie")
                }
            }

            RunStatus.Saving -> CenterHint(stringResource(R.string.larkam_saving))

            RunStatus.Saved -> {
                CenterHint(stringResource(R.string.larkam_saved))
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.larkam_done))
                }
            }

            is RunStatus.Error -> {
                CenterHint(stringResource(status.messageResId), isError = true)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.larkam_done))
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CenterHint(text: String, isError: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** HH:MM:SS for elapsed count. */
private fun formatElapsed(sec: Long): String = "%02d:%02d:%02d".format(sec / 3600, (sec % 3600) / 60, sec % 60)

/** KM with 2 decimals always (spec: 2.45 KM). */
private fun formatDistance(meters: Double): String = "%.2f km".format(meters / 1000.0)

/** Pace as mm:ss /km */
private fun formatPace(state: LarkamUiState): String = state.paceFormatted

/**
 * osmdroid map drawing the run so far as a [Polyline] with a marker at the latest
 * fix. Stateless: it derives everything from [path].
 *
 * Render fixes: MapView is owned by AndroidView factory/onRelease (no
 * "already has a parent" crash), tiles only reload on theme flip, and the
 * camera only animates when the fix moved > [FOLLOW_THRESHOLD_M] while
 * auto-follow is on — so panning no longer snaps back every second.
 */
@Suppress("ClickableViewAccessibility")
@Composable
private fun RunPathMap(path: List<RunPoint>, modifier: Modifier = Modifier) {
    if (LocalInspectionMode.current) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Peta rute", style = MaterialTheme.typography.labelLarge)
            }
        }
        return
    }

    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val description = stringResource(R.string.cd_larkam_map)
    val isDark = isSystemInDarkTheme()
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant

    remember(appContext) { ensureOsmdroidConfigured(appContext); true }
    LaunchedEffect(appContext) { com.gynda.fridaystm.ui.component.configureOsmdroid(appContext) }

    val dot = remember(appContext, primaryColor) {
        userDotDrawable(appContext, primaryColor.toArgb())
    }

    var isMapInitialCentered by remember { mutableStateOf(false) }
    var isAutoFollow by remember { mutableStateOf(true) }
    var appliedDark by remember { mutableStateOf<Boolean?>(null) }
    var liveMap by remember { mutableStateOf<MapView?>(null) }

    Box(modifier = modifier) {
        Card(
            modifier = Modifier.fillMaxSize(),
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
                            controller.setZoom(DEFAULT_ZOOM)
                            controller.setCenter(DEFAULT_CENTER)
                            setOnTouchListener { v, event ->
                                if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN ||
                                    event.actionMasked == android.view.MotionEvent.ACTION_MOVE
                                ) {
                                    isAutoFollow = false
                                }
                                v.parent?.requestDisallowInterceptTouchEvent(true)
                                false
                            }
                            val line = Polyline(this).apply {
                                outlinePaint.color = primaryColor.toArgb()
                                outlinePaint.strokeWidth = LINE_STROKE
                            }
                            val marker = Marker(this).apply {
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                icon = dot
                            }
                            tag = RunMapHolder(line, marker, null, null, -1)
                            overlays.add(line)
                            liveMap = this
                            onResume()
                        }
                    },
                    update = { map ->
                        if (appliedDark != isDark) {
                            try {
                                map.setTileSource(defaultTileSource(isDark))
                            } catch (_: Exception) {
                            }
                            appliedDark = isDark
                        }
                        map.setBackgroundColor(surfaceColor.toArgb())

                        val holder = (map.tag as? RunMapHolder) ?: run {
                            val line = Polyline(map).apply {
                                outlinePaint.color = primaryColor.toArgb()
                                outlinePaint.strokeWidth = LINE_STROKE
                            }
                            val marker = Marker(map).apply {
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            }
                            RunMapHolder(line, marker, null, null, -1).also {
                                map.tag = it
                                if (!map.overlays.contains(line)) map.overlays.add(line)
                            }
                        }
                        holder.line.outlinePaint.color = primaryColor.toArgb()
                        if (holder.marker.icon !== dot) holder.marker.icon = dot

                        var needsInvalidate = false
                        val last = path.lastOrNull()
                        val lastPoint = last?.let { GeoPoint(it.lat, it.lng) }

                        // Polyline: rebuild only when the trail actually grew.
                        if (holder.renderedSize != path.size ||
                            (lastPoint != null && holder.renderedLast != lastPoint)
                        ) {
                            holder.line.setPoints(path.map { GeoPoint(it.lat, it.lng) })
                            holder.renderedSize = path.size
                            holder.renderedLast = lastPoint
                            if (!map.overlays.contains(holder.line)) map.overlays.add(holder.line)
                            needsInvalidate = true
                        } else if (!map.overlays.contains(holder.line) && path.isNotEmpty()) {
                            map.overlays.add(holder.line)
                            needsInvalidate = true
                        }

                        if (lastPoint != null) {
                            val prev = holder.marker.position
                            if (prev == null || prev.distanceToAsDouble(lastPoint) > 1.0) {
                                holder.marker.position = lastPoint
                                needsInvalidate = true
                            }
                            if (!map.overlays.contains(holder.marker)) {
                                map.overlays.add(holder.marker)
                                needsInvalidate = true
                            }

                            if (!isMapInitialCentered) {
                                try {
                                    map.controller.setZoom(DEFAULT_ZOOM)
                                    map.controller.setCenter(lastPoint)
                                } catch (_: Exception) {
                                }
                                holder.lastCamera = lastPoint
                                isMapInitialCentered = true
                                needsInvalidate = true
                            } else if (isAutoFollow) {
                                val camPrev = holder.lastCamera
                                if (camPrev == null ||
                                    camPrev.distanceToAsDouble(lastPoint) > FOLLOW_THRESHOLD_M
                                ) {
                                    try {
                                        map.controller.animateTo(lastPoint)
                                    } catch (_: Exception) {
                                    }
                                    holder.lastCamera = lastPoint
                                    needsInvalidate = true
                                }
                            }
                        }
                        if (needsInvalidate) map.invalidate()
                    },
                    onRelease = { map ->
                        if (liveMap === map) liveMap = null
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

                Text(
                    text = MAP_ATTRIBUTION,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                )

                if (path.isEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        shape = RoundedCornerShape(12.dp),
                        shadowElevation = 4.dp,
                        modifier = Modifier.align(Alignment.Center),
                    ) {
                        Text(
                            text = stringResource(R.string.larkam_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }

        // Recenter FAB appears when user pans away from auto-follow.
        if (!isAutoFollow && path.isNotEmpty()) {
            val last = path.last()
            FilledTonalIconButton(
                onClick = {
                    isAutoFollow = true
                    try {
                        liveMap?.controller?.animateTo(GeoPoint(last.lat, last.lng))
                    } catch (_: Exception) {
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Place,
                    contentDescription = stringResource(R.string.cd_minimap_recenter),
                )
            }
        }
    }
}

private class RunMapHolder(
    val line: Polyline,
    val marker: Marker,
    var renderedLast: GeoPoint?,
    var lastCamera: GeoPoint?,
    var renderedSize: Int,
)

private const val DEFAULT_ZOOM = 17.0
private const val LINE_STROKE = 8f
private const val FOLLOW_THRESHOLD_M = 8.0

/** School area fallback so an empty trail never opens on the Atlantic (0,0). */
private val DEFAULT_CENTER = GeoPoint(-6.875, 107.54)

@Preview(showBackground = true, name = "Larkam · Idle")
@Composable
private fun LarkamIdlePreview() {
    FridaySTMTheme {
        LarkamContent(
            state = LarkamUiState(),
            onStart = {},
            onStop = {},
            onDone = {},
        )
    }
}

@Preview(showBackground = true, name = "Larkam · Running")
@Composable
private fun LarkamRunningPreview() {
    FridaySTMTheme {
        LarkamContent(
            state = LarkamUiState(
                status = RunStatus.Running,
                elapsedSec = 372,
                distanceMeters = 1240.0,
                path = listOf(RunPoint(-6.87, 107.54), RunPoint(-6.871, 107.541)),
            ),
            onStart = {},
            onStop = {},
            onDone = {},
        )
    }
}
