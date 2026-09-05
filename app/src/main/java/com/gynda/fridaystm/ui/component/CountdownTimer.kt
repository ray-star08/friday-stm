package com.gynda.fridaystm.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gynda.fridaystm.R
import com.gynda.fridaystm.ui.theme.FridaySTMTheme
import kotlinx.coroutines.delay

/**
 * A live countdown to a phase deadline, ticking every second (Milestone 5.3).
 *
 * Kept out of `domain/`: it is pure UI. The *target* is passed in as an epoch
 * millis value (computed by the caller from `TimeProvider` + `PhaseSchedule`), so
 * this widget owns only the per-second display state via `remember` +
 * `LaunchedEffect` — no clock reading in the ViewModel, no business logic here.
 *
 * @param targetEpochMillis the deadline to count down to.
 * @param label prefix text, e.g. "Batas check-in".
 * @param nowProvider injectable clock so previews/tests are deterministic.
 */
@Composable
fun CountdownTimer(
    targetEpochMillis: Long,
    label: String,
    modifier: Modifier = Modifier,
    nowProvider: () -> Long = { System.currentTimeMillis() },
) {
    var remainingMillis by remember(targetEpochMillis) {
        mutableLongStateOf((targetEpochMillis - nowProvider()).coerceAtLeast(0L))
    }

    LaunchedEffect(targetEpochMillis) {
        while (remainingMillis > 0L) {
            delay(1_000L)
            remainingMillis = (targetEpochMillis - nowProvider()).coerceAtLeast(0L)
        }
    }

    val formatted = formatDuration(remainingMillis)
    val announce = stringResource(R.string.cd_countdown, label, formatted)

    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = announce },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = formatted,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** `HH:mm:ss` when ≥ 1h, else `mm:ss`. */
private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%02d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}

@Preview(showBackground = true)
@Composable
private fun CountdownTimerPreview() {
    FridaySTMTheme {
        // Fixed 5-minute remainder, deterministic for the preview.
        CountdownTimer(
            targetEpochMillis = 5 * 60 * 1_000L,
            label = "Batas check-in Apel",
            nowProvider = { 0L },
        )
    }
}
