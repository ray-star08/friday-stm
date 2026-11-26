package com.gynda.fridaystm.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gynda.fridaystm.R
import com.gynda.fridaystm.domain.Activity
import com.gynda.fridaystm.domain.FridayPhase
import com.gynda.fridaystm.ui.theme.FridaySTMTheme
import com.gynda.fridaystm.ui.theme.Spacing
import kotlinx.coroutines.delay

/**
 * Geofence note rendered inside the hero: whether the latest fix is inside the
 * current fence plus the already-localized short text ("Di dalam Lapangan
 * Utama" / "120 m dari Lapangan Utama"). Pure data — the ViewModel owns the
 * inside/distance decision.
 */
@Immutable
data class HeroGeofenceNote(val inside: Boolean, val text: String)

/**
 * Single visual anchor of the Home screen: phase + activity, its live countdown
 * progress, the geofence note and the one primary call-to-action — one card
 * instead of the previous stack of phase card / stepper / countdown / hint.
 *
 * Domain-aware but stateless (SKILL.md §4): every value arrives as a parameter
 * and every intent leaves through [onAction], so each phase is previewable.
 */
@Composable
fun PhaseHeroCard(
    phase: FridayPhase,
    activeActivity: Activity?,
    modifier: Modifier = Modifier,
    isSpecialWeek: Boolean = false,
    startEpochMillis: Long? = null,
    deadlineEpochMillis: Long? = null,
    nowProvider: () -> Long = { System.currentTimeMillis() },
    windowText: String? = null,
    geofence: HeroGeofenceNote? = null,
    actionLabel: String? = null,
    actionIcon: ImageVector? = null,
    actionEnabled: Boolean = false,
    actionLoading: Boolean = false,
    onAction: () -> Unit = {},
) {
    val visuals = heroVisuals(phase, activeActivity)
    val scheme = MaterialTheme.colorScheme
    val container = when (phase) {
        FridayPhase.PEMBIASAAN -> scheme.primary
        FridayPhase.CHECKOUT -> scheme.tertiary
        FridayPhase.DONE -> scheme.secondaryContainer
        FridayPhase.BEFORE,
        FridayPhase.NOT_FRIDAY,
        -> scheme.surfaceContainerHigh
    }
    val onContainer = when (phase) {
        FridayPhase.PEMBIASAAN -> scheme.onPrimary
        FridayPhase.CHECKOUT -> scheme.onTertiary
        FridayPhase.DONE -> scheme.onSecondaryContainer
        FridayPhase.BEFORE,
        FridayPhase.NOT_FRIDAY,
        -> scheme.onSurface
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = container,
            contentColor = onContainer,
        ),
    ) {
        Column(Modifier.padding(Spacing.s20)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(onContainer.copy(alpha = 0.16f)),
                ) {
                    Icon(
                        imageVector = visuals.icon,
                        contentDescription = null, // decorative; the heading names the phase
                        tint = onContainer,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (isSpecialWeek && phase != FridayPhase.NOT_FRIDAY) {
                    val description = stringResource(R.string.cd_special_week_badge)
                    Surface(
                        shape = RoundedCornerShape(percent = 50),
                        color = onContainer.copy(alpha = 0.16f),
                        contentColor = onContainer,
                        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = Spacing.s12, vertical = Spacing.s8),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DateRange,
                                contentDescription = null, // decorative; the pill carries the label
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(Spacing.s8))
                            Text(
                                text = stringResource(R.string.home_special_week_badge),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacing.s16))
            Text(
                text = visuals.title,
                style = MaterialTheme.typography.headlineMedium,
                color = onContainer,
            )
            Spacer(Modifier.height(Spacing.s4))
            Text(
                text = visuals.body,
                style = MaterialTheme.typography.bodyMedium,
                color = onContainer.copy(alpha = 0.86f),
            )

            if (startEpochMillis != null && deadlineEpochMillis != null &&
                deadlineEpochMillis > startEpochMillis
            ) {
                Spacer(Modifier.height(Spacing.s20))
                PhaseCountdown(
                    phase = phase,
                    startEpochMillis = startEpochMillis,
                    deadlineEpochMillis = deadlineEpochMillis,
                    nowProvider = nowProvider,
                    windowText = windowText,
                    contentColor = onContainer,
                )
            }

            geofence?.let { note ->
                Spacer(Modifier.height(Spacing.s16))
                GeofencePill(note = note, contentColor = onContainer)
            }

            if (actionLabel != null) {
                val interactive = actionEnabled || actionLoading
                Spacer(Modifier.height(Spacing.s20))
                BigActionButton(
                    text = actionLabel,
                    onClick = onAction,
                    enabled = actionEnabled,
                    loading = actionLoading,
                    icon = actionIcon,
                    containerColor = if (interactive) MaterialTheme.colorScheme.surface else onContainer,
                    contentColor = if (interactive) MaterialTheme.colorScheme.primary else onContainer,
                )
            }
        }
    }
}

/** Live progress + remaining time for the current phase window. */
@Composable
private fun PhaseCountdown(
    phase: FridayPhase,
    startEpochMillis: Long,
    deadlineEpochMillis: Long,
    nowProvider: () -> Long,
    windowText: String?,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    var now by remember(deadlineEpochMillis) { mutableLongStateOf(nowProvider()) }
    LaunchedEffect(startEpochMillis, deadlineEpochMillis) {
        while (true) {
            now = nowProvider()
            delay(1_000)
        }
    }

    val remaining = (deadlineEpochMillis - now).coerceAtLeast(0L)
    val fraction = ((now - startEpochMillis).toFloat() / (deadlineEpochMillis - startEpochMillis).toFloat())
        .coerceIn(0f, 1f)
    val timeText = if (remaining > 0) {
        stringResource(R.string.home_time_left, formatDuration(remaining))
    } else {
        stringResource(R.string.home_time_up)
    }
    val windowLabel = stringResource(
        if (phase == FridayPhase.CHECKOUT) R.string.countdown_checkout
        else R.string.countdown_pembiasaan,
    )
    val description = stringResource(R.string.cd_countdown, windowLabel, timeText)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
    ) {
        LinearProgressIndicator(
            progress = { fraction },
            color = contentColor,
            trackColor = contentColor.copy(alpha = 0.24f),
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(percent = 50)),
        )
        Spacer(Modifier.height(Spacing.s8))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = timeText,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
            )
            if (windowText != null) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = windowText,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.72f),
                )
            }
        }
    }
}

/** Short geofence badge: inside (check) or the live distance to the fence. */
@Composable
private fun GeofencePill(
    note: HeroGeofenceNote,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = contentColor.copy(alpha = 0.16f),
        contentColor = contentColor,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Spacing.s12, vertical = Spacing.s8),
        ) {
            Icon(
                imageVector = if (note.inside) Icons.Filled.CheckCircle else Icons.Filled.LocationOn,
                contentDescription = null, // the text already reads the status
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(Spacing.s8))
            Text(text = note.text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Heading, body and icon of the hero for a phase; purely presentational. */
@Composable
private fun heroVisuals(phase: FridayPhase, activeActivity: Activity?): HeroVisuals {
    val activityLabel = activeActivity?.let { stringResource(activityLabelRes(it)) }
    return when (phase) {
        FridayPhase.PEMBIASAAN -> HeroVisuals(
            title = activityLabel ?: stringResource(R.string.phase_pembiasaan_title_fallback),
            body = stringResource(R.string.phase_pembiasaan_body),
            icon = when (activeActivity) {
                Activity.LARKAM -> Icons.Filled.LocationOn
                Activity.SENAM -> Icons.Filled.Favorite
                else -> Icons.Filled.DateRange
            },
        )

        FridayPhase.CHECKOUT -> HeroVisuals(
            title = stringResource(R.string.phase_checkout_title),
            body = stringResource(R.string.phase_checkout_body),
            icon = Icons.Filled.CheckCircle,
        )

        FridayPhase.BEFORE -> HeroVisuals(
            title = stringResource(R.string.phase_before_title),
            body = stringResource(R.string.phase_before_body),
            icon = Icons.Filled.DateRange,
        )

        FridayPhase.DONE -> HeroVisuals(
            title = stringResource(R.string.phase_done_title),
            body = stringResource(R.string.phase_done_body),
            icon = Icons.Filled.CheckCircle,
        )

        FridayPhase.NOT_FRIDAY -> HeroVisuals(
            title = stringResource(R.string.phase_not_friday_title),
            body = stringResource(R.string.phase_not_friday_body),
            icon = Icons.Filled.Info,
        )
    }
}

private data class HeroVisuals(
    val title: String,
    val body: String,
    val icon: ImageVector,
)

/** `H:MM:SS` for the phase windows (never > 1h), `MM:SS` as it shortens. */
private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

@Preview(showBackground = true, name = "PhaseHeroCard · Pembiasaan")
@Composable
private fun PhaseHeroCardPembiasaanPreview() {
    FridaySTMTheme {
        PhaseHeroCard(
            phase = FridayPhase.PEMBIASAAN,
            activeActivity = Activity.TALIM,
            startEpochMillis = 0L,
            deadlineEpochMillis = 60L * 60L * 1_000L,
            windowText = "06.30 – 08.00",
            geofence = HeroGeofenceNote(inside = true, text = "Di dalam Lapangan Utama"),
            actionLabel = "Check-in Ta'lim",
            actionIcon = Icons.Filled.LocationOn,
            actionEnabled = true,
        )
    }
}

@Preview(showBackground = true, name = "PhaseHeroCard · Check-out")
@Composable
private fun PhaseHeroCardCheckoutPreview() {
    FridaySTMTheme {
        PhaseHeroCard(
            phase = FridayPhase.CHECKOUT,
            activeActivity = Activity.LARKAM,
            startEpochMillis = 0L,
            deadlineEpochMillis = 30L * 60L * 1_000L,
            windowText = "08.00 – 08.30",
            actionLabel = "Check-out Sekarang",
            actionIcon = Icons.Filled.CheckCircle,
            actionEnabled = true,
        )
    }
}

@Preview(showBackground = true, name = "PhaseHeroCard · Bukan Jumat")
@Composable
private fun PhaseHeroCardNotFridayPreview() {
    FridaySTMTheme {
        PhaseHeroCard(phase = FridayPhase.NOT_FRIDAY, activeActivity = null)
    }
}

@Preview(showBackground = true, name = "PhaseHeroCard · Selesai")
@Composable
private fun PhaseHeroCardDonePreview() {
    FridaySTMTheme {
        PhaseHeroCard(
            phase = FridayPhase.PEMBIASAAN,
            activeActivity = Activity.LARKAM,
            isSpecialWeek = true,
            geofence = HeroGeofenceNote(inside = false, text = "120 m dari Titik Larkam"),
            actionLabel = "Larkam Tercatat",
            actionIcon = Icons.Filled.CheckCircle,
            actionEnabled = false,
        )
    }
}
