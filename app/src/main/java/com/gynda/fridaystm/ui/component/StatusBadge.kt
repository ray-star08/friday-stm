package com.gynda.fridaystm.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gynda.fridaystm.data.model.StudentPresenceStatus
import com.gynda.fridaystm.util.IzinStatus

/**
 * Reusable M3 badge — pill, tonal, no hard-coded colors.
 * Centralizes the duplicated `Surface(RoundedCornerShape(50))` logic
 * from TeacherDashboardScreen.kt:295 & IzinApprovalScreen.kt:255.
 */
@Composable
fun StudentStatusBadge(
    status: StudentPresenceStatus,
    time: String? = null,
    modifier: Modifier = Modifier,
) {
    val (label, container, content) = when (status) {
        StudentPresenceStatus.HADIR -> {
            val t = if (!time.isNullOrBlank()) "Hadir - $time WIB" else "Hadir"
            Triple(t, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
        }
        StudentPresenceStatus.IZIN -> Triple(
            "Izin / Sakit",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
        )
        StudentPresenceStatus.BELUM -> Triple(
            "Belum Absen",
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
        IzinStatus.APPROVED -> Triple("Disetujui", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        IzinStatus.REJECTED -> Triple("Ditolak", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        else -> Triple("Menunggu", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
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
