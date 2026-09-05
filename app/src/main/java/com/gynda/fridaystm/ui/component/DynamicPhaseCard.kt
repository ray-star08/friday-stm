package com.gynda.fridaystm.ui.component

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gynda.fridaystm.R
import com.gynda.fridaystm.domain.Activity
import com.gynda.fridaystm.domain.FridayPhase
import com.gynda.fridaystm.ui.theme.FridaySTMTheme

/**
 * The hero card on the Home screen: its color, icon, and copy all change with the
 * current [FridayPhase]. During [FridayPhase.PEMBIASAAN] the title shows the
 * specific rotated [activeActivity] (Ta'lim / Larkam / Senam).
 *
 * Stateless & reusable (SKILL.md §4): it takes plain domain values and applies
 * the passed [modifier] to its root, so it is fully previewable without a ViewModel.
 */
@Composable
fun DynamicPhaseCard(
    phase: FridayPhase,
    activeActivity: Activity?,
    modifier: Modifier = Modifier,
) {
    val visuals = phaseVisuals(phase, activeActivity)
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = visuals.container),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(visuals.accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = visuals.icon,
                    contentDescription = stringResource(R.string.cd_phase_icon),
                    tint = visuals.accent,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = visuals.eyebrow,
                    style = MaterialTheme.typography.labelLarge,
                    color = visuals.accent,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = visuals.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = visuals.onContainer,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = visuals.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = visuals.onContainer.copy(alpha = 0.82f),
                )
            }
        }
    }
}

/** Resolved look-and-feel for one phase; kept internal to this component. */
private data class PhaseVisuals(
    val eyebrow: String,
    val title: String,
    val body: String,
    val icon: ImageVector,
    val container: Color,
    val onContainer: Color,
    val accent: Color,
)

@Composable
private fun phaseVisuals(phase: FridayPhase, activeActivity: Activity?): PhaseVisuals {
    val scheme = MaterialTheme.colorScheme
    return when (phase) {
        FridayPhase.BEFORE -> PhaseVisuals(
            eyebrow = stringResource(R.string.phase_before_eyebrow),
            title = stringResource(R.string.phase_before_title),
            body = stringResource(R.string.phase_before_body),
            icon = Icons.Filled.DateRange,
            container = scheme.surfaceVariant,
            onContainer = scheme.onSurfaceVariant,
            accent = scheme.primary,
        )

        FridayPhase.PEMBIASAAN -> PhaseVisuals(
            eyebrow = stringResource(R.string.phase_pembiasaan_eyebrow),
            title = activeActivity
                ?.let { stringResource(activityLabelRes(it)) }
                ?: stringResource(R.string.phase_pembiasaan_title_fallback),
            body = stringResource(R.string.phase_pembiasaan_body),
            icon = Icons.Filled.LocationOn,
            container = scheme.secondaryContainer,
            onContainer = scheme.onSecondaryContainer,
            accent = scheme.secondary,
        )

        FridayPhase.CHECKOUT -> PhaseVisuals(
            eyebrow = stringResource(R.string.phase_checkout_eyebrow),
            title = stringResource(R.string.phase_checkout_title),
            body = stringResource(R.string.phase_checkout_body),
            icon = Icons.Filled.CheckCircle,
            container = scheme.tertiaryContainer,
            onContainer = scheme.onTertiaryContainer,
            accent = scheme.tertiary,
        )

        FridayPhase.DONE -> PhaseVisuals(
            eyebrow = stringResource(R.string.phase_done_eyebrow),
            title = stringResource(R.string.phase_done_title),
            body = stringResource(R.string.phase_done_body),
            icon = Icons.Filled.CheckCircle,
            container = scheme.secondaryContainer,
            onContainer = scheme.onSecondaryContainer,
            accent = scheme.secondary,
        )

        FridayPhase.NOT_FRIDAY -> PhaseVisuals(
            eyebrow = stringResource(R.string.phase_not_friday_eyebrow),
            title = stringResource(R.string.phase_not_friday_title),
            body = stringResource(R.string.phase_not_friday_body),
            icon = Icons.Filled.DateRange,
            container = scheme.surfaceVariant,
            onContainer = scheme.onSurfaceVariant,
            accent = scheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true, name = "Phase · Pembiasaan (Larkam)")
@Composable
private fun DynamicPhaseCardPembiasaanPreview() {
    FridaySTMTheme {
        DynamicPhaseCard(
            phase = FridayPhase.PEMBIASAAN,
            activeActivity = Activity.LARKAM,
            modifier = Modifier.padding(16.dp),
        )
    }
}
