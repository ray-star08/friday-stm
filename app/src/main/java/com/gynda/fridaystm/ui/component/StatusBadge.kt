package com.gynda.fridaystm.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gynda.fridaystm.R
import com.gynda.fridaystm.data.model.StudentPresenceStatus
import com.gynda.fridaystm.util.IzinStatus

/**
 * Reusable M3 badge — pill, tonal, no hard-coded colors.
 * Centralizes the duplicated `Surface(RoundedCornerShape(50))` logic
 * from TeacherDashboardScreen.kt:295 & IzinApprovalScreen.kt:255.
 */
@Suppress("ModifierParameter") // SKILL §4.2: modifier last; lint wants modifier first optional.
@Composable
fun StudentStatusBadge(
    status: StudentPresenceStatus,
    time: String? = null,
    modifier: Modifier = Modifier,
) {
    val (label, container, content) = when (status) {
        StudentPresenceStatus.HADIR -> {
            val t = if (!time.isNullOrBlank()) {
                stringResource(R.string.student_badge_complete_time, time)
            } else {
                stringResource(R.string.history_day_complete)
            }
            Triple(t, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
        }
        StudentPresenceStatus.PARTIAL -> Triple(stringResource(R.string.student_badge_pending_checkout), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        StudentPresenceStatus.LEGACY -> Triple(stringResource(R.string.student_badge_legacy_selfie), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        StudentPresenceStatus.NEEDS_REVIEW -> Triple(stringResource(R.string.history_day_needs_review), MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        StudentPresenceStatus.IZIN -> Triple(
            stringResource(R.string.student_badge_izin),
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
        )
        StudentPresenceStatus.BELUM -> Triple(
            stringResource(R.string.student_badge_absent),
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )
    }
    Badge(label = label, containerColor = container, contentColor = content, modifier = modifier)
}

@Composable
fun IzinStatusBadge(
    statusWire: String,
    modifier: Modifier = Modifier,
) {
    val (label, container, content) = when (statusWire) {
        IzinStatus.APPROVED -> Triple(stringResource(R.string.izin_status_approved), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        IzinStatus.REJECTED -> Triple(stringResource(R.string.izin_status_rejected), MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        else -> Triple(stringResource(R.string.izin_status_pending), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
    }
    Badge(label = label, containerColor = container, contentColor = content, modifier = modifier)
}

@Composable
private fun Badge(
    label: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(50),
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
