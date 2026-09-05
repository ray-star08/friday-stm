package com.gynda.fridaystm.ui.screen

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gynda.fridaystm.R
import com.gynda.fridaystm.ui.component.configureOsmdroid
import com.gynda.fridaystm.ui.theme.FridaySTMTheme
import com.gynda.fridaystm.ui.theme.PrimaryLight
import com.gynda.fridaystm.viewmodel.LarkamUiState
import com.gynda.fridaystm.viewmodel.LarkamViewModel
import com.gynda.fridaystm.viewmodel.RunPoint
import com.gynda.fridaystm.viewmodel.RunStatus
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * Larkam — stateful holder. Owns the [LarkamViewModel], renders the live run
 * (map + duration/distance) and a Start/Stop control. On a saved run it shows a
 * Toast confirmation and a "done" button back to Home (raised via [onDone]).
 */
@Composable
fun LarkamTrackingScreen(
    viewModel: LarkamViewModel,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(state.status) {
        if (state.status == RunStatus.Saved) {
            Toast.makeText(context, context.getString(R.string.larkam_save_success), Toast.LENGTH_SHORT).show()
        }
    }
    LarkamContent(
        state = state,
        onStart = viewModel::onStart,
        onStop = viewModel::onStop,
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
                .weight(1f)
                .clip(RoundedCornerShape(16.dp)),
        )
        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stat(label = stringResource(R.string.larkam_duration), value = formatElapsed(state.elapsedSec))
            Stat(label = stringResource(R.string.larkam_distance), value = formatDistance(state.distanceMeters))
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

            RunStatus.Running -> Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.larkam_stop))
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

/** mm:ss for a whole-second elapsed count. */
private fun formatElapsed(sec: Long): String = "%02d:%02d".format(sec / 60, sec % 60)

/** Meters when < 1 km, else km with two decimals. */
private fun formatDistance(meters: Double): String =
    if (meters < 1_000) "${meters.toInt()} m" else "%.2f km".format(meters / 1_000)

/**
 * osmdroid map drawing the run so far as a [Polyline] with a marker at the latest
 * fix. Stateless: it derives everything from [path] (SKILL.md §4.2). Mirrors the
 * GeofenceMiniMap lifecycle/config handling so tiles load and the view never leaks.
 */
@Composable
private fun RunPathMap(path: List<RunPoint>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val description = stringResource(R.string.cd_larkam_map)

    LaunchedEffect(Unit) { configureOsmdroid(context) }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(DEFAULT_ZOOM)
        }
    }
    val line = remember(mapView) {
        Polyline(mapView).apply {
            outlinePaint.color = PrimaryLight.toArgb()
            outlinePaint.strokeWidth = LINE_STROKE
        }
    }
    val marker = remember(mapView) {
        Marker(mapView).apply { setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM) }
    }

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
            val points = path.map { GeoPoint(it.lat, it.lng) }
            line.setPoints(points)
            if (!map.overlays.contains(line)) map.overlays.add(line)

            val last = points.lastOrNull()
            if (last != null) {
                marker.position = last
                if (!map.overlays.contains(marker)) map.overlays.add(marker)
                if (map.mapCenter.latitude != last.latitude || map.mapCenter.longitude != last.longitude) {
                    map.controller.animateTo(last)
                }
            }
            map.invalidate()
        },
    )
}

private const val DEFAULT_ZOOM = 17.0
private const val LINE_STROKE = 8f

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
