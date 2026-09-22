package com.gynda.fridaystm.ui.screen

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import com.gynda.fridaystm.ui.theme.ComponentSize
import com.gynda.fridaystm.ui.theme.Spacing
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.gynda.fridaystm.R
import com.gynda.fridaystm.data.model.ApelStamp
import com.gynda.fridaystm.data.model.AttendanceRecord
import com.gynda.fridaystm.data.model.AttendanceDay
import com.gynda.fridaystm.data.model.AttendanceDayStatus
import com.gynda.fridaystm.data.model.schoolCaptureTime
import java.time.format.DateTimeFormatter
import androidx.compose.ui.platform.testTag
import com.gynda.fridaystm.data.model.AttendanceDayProjector
import com.gynda.fridaystm.data.model.PembiasaanStamp
import com.gynda.fridaystm.ui.theme.FridaySTMTheme
import com.gynda.fridaystm.util.ActivityType
import com.gynda.fridaystm.viewmodel.HistoryUiState
import com.gynda.fridaystm.viewmodel.HistoryViewModel

/**
 * History — stateful holder (SKILL.md §4.1). Owns [HistoryViewModel], collects
 * state lifecycle-aware, and delegates rendering to the stateless [HistoryContent].
 */
@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryContent(state = uiState, modifier = modifier)
}

/** History — stateless content; renders purely from [state] so it is previewable. */
@Composable
fun HistoryContent(
    state: HistoryUiState,
    modifier: Modifier = Modifier,
) {
    var selected by remember(state) { mutableStateOf<AttendanceDay?>(null) }
    LazyColumn(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(Spacing.s20),
        verticalArrangement = Arrangement.spacedBy(Spacing.s12),
    ) {
        item {
            Column {
                Text(stringResource(R.string.redesign_brand), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(Spacing.s8))
                Text(stringResource(R.string.redesign_history_heading), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.redesign_history_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        when (state) {
            HistoryUiState.Loading -> item { CenteredSpinner() }
            HistoryUiState.Empty -> item { CenteredMessage(stringResource(R.string.history_empty)) }
            is HistoryUiState.Error -> item { CenteredMessage(stringResource(state.messageResId)) }
            is HistoryUiState.Success -> items(state.records, key = { it.id }) { day ->
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                    Text(dayLabel(day.status), style = MaterialTheme.typography.labelLarge,
                        color = if (day.status == AttendanceDayStatus.NEEDS_REVIEW) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    if (day.attendance != null) {
                        AttendanceHistoryCard(day.attendance, onClick = { selected = day })
                    } else {
                        Card(onClick = { selected = day }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(Spacing.s16)) {
                                Text(day.date, style = MaterialTheme.typography.titleMedium)
                                Text(day.time.ifBlank { "Jam tidak tersedia" }, style = MaterialTheme.typography.bodyMedium)
                                Text("Fase dan penyelesaian tidak tersedia pada data lama.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
    selected?.let { day ->
        if (day.attendance != null) {
            AttendanceDetailDialog(day = day, onDismiss = { selected = null })
        } else {
            Dialog(onDismissRequest = { selected = null }) {
                Card(Modifier.testTag("legacy-attendance-detail")) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(Spacing.s16)) {
                        Text(day.date, style = MaterialTheme.typography.titleLarge)
                        Text(dayLabel(day.status), style = MaterialTheme.typography.labelLarge)
                        Text(day.time)
                        Text("Fase dan penyelesaian tidak tersedia pada data lama.")
                        if (day.imageUrl.isNotBlank()) AsyncImage(day.imageUrl, "Bukti selfie lama", modifier = Modifier.fillMaxWidth().height(220.dp))
                        TextButton(onClick = { selected = null }) { Text("Tutup") }
                    }
                }
            }
        }
    }
}

private fun dayLabel(status: AttendanceDayStatus): String = when (status) {
    AttendanceDayStatus.COMPLETE -> "Lengkap"
    AttendanceDayStatus.PARTIAL -> "Sebagian"
    AttendanceDayStatus.NEEDS_REVIEW -> "Perlu ditinjau"
    AttendanceDayStatus.LEGACY -> "Data lama"
}

/**
 * One day's attendance summary: date header plus a row per completed phase
 * (Apel / Pembiasaan) showing time, status, selfie thumbnail and coordinates.
 */
@Composable
private fun AttendanceHistoryCard(
    record: AttendanceRecord,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(ComponentSize.border, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = record.date,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))

            val apel = record.apel
            if (apel?.checkedIn == true) {
                PhaseRow(
                    label = stringResource(R.string.step_apel),
                    time = apel.time,
                    valid = apel.valid,
                    selfieUrl = apel.selfieUrl,
                    lat = apel.lat,
                    lng = apel.lng,
                )
            }

            val pemb = record.pembiasaan
            if (pemb?.checkedIn == true) {
                if (apel?.checkedIn == true) Spacer(Modifier.height(12.dp))
                PhaseRow(
                    label = pembiasaanLabel(pemb.activity),
                    time = pemb.time,
                    valid = pemb.valid,
                    selfieUrl = pemb.selfieUrl,
                    lat = pemb.lat,
                    lng = pemb.lng,
                )
            }

            record.checkout?.takeIf { it.checkedOut }?.let { checkout ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.history_checkout_at, checkout.time),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PhaseRow(
    label: String,
    time: String,
    valid: Boolean,
    selfieUrl: String,
    lat: Double,
    lng: Double,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (selfieUrl.isNotBlank()) {
            AsyncImage(
                model = selfieUrl,
                contentDescription = stringResource(R.string.cd_history_selfie, label),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringResource(R.string.history_phase_time, time),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.history_phase_location, lat, lng),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ValidityBadge(valid)
    }
}

@Composable
private fun ValidityBadge(valid: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val container = if (valid) scheme.secondaryContainer else scheme.errorContainer
    val content = if (valid) scheme.onSecondaryContainer else scheme.onErrorContainer
    Surface(color = container, shape = RoundedCornerShape(50), modifier = modifier) {
        Text(
            text = stringResource(if (valid) R.string.history_valid else R.string.history_invalid),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** Fullscreen detail for one attendance day — large selfies + map open. */
@Composable
private fun AttendanceDetailDialog(
    day: AttendanceDay,
    onDismiss: () -> Unit,
) {
    val record = day.attendance ?: return
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.testTag("canonical-attendance-detail").fillMaxWidth().padding(16.dp)) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text(record.date, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(dayLabel(day.status), style = MaterialTheme.typography.labelLarge)
                Text("Kelas ${record.grade} • ${record.status}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                record.apel?.takeIf { it.checkedIn }?.let { apel ->
                    Text(stringResource(R.string.step_apel), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    if (apel.selfieUrl.isNotBlank()) {
                        AsyncImage(model = apel.selfieUrl, contentDescription = "Selfie Apel", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(stringResource(R.string.history_phase_time, apel.time), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.history_phase_location, apel.lat, apel.lng), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        ValidityBadge(apel.valid)
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            val uri = "geo:${apel.lat},${apel.lng}?q=${apel.lat},${apel.lng}".toUri()
                            try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: Exception) {}
                        }) { Icon(Icons.Filled.LocationOn, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Maps") }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                record.pembiasaan?.takeIf { it.checkedIn }?.let { pemb ->
                    Text(pembiasaanLabel(pemb.activity), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    if (pemb.selfieUrl.isNotBlank()) {
                        AsyncImage(model = pemb.selfieUrl, contentDescription = "Selfie Pembiasaan", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(stringResource(R.string.history_phase_time, pemb.time), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.history_phase_location, pemb.lat, pemb.lng), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        ValidityBadge(pemb.valid)
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            val uri = "geo:${pemb.lat},${pemb.lng}?q=${pemb.lat},${pemb.lng}".toUri()
                            try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: Exception) {}
                        }) { Icon(Icons.Filled.LocationOn, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Maps") }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                record.checkout?.takeIf { it.checkedOut }?.let { co ->
                    Text("Check-out: ${co.time}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                }
                day.presensi?.let { legacy ->
                    Text("Bukti umum data lama", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    val captureTime = schoolCaptureTime(legacy.timestamp).format(DateTimeFormatter.ofPattern("HH:mm"))
                    Text("Jam capture (WIB): $captureTime", style = MaterialTheme.typography.bodySmall)
                    if (legacy.imageUrl.isNotBlank()) {
                        AsyncImage(model = legacy.imageUrl, contentDescription = "Bukti foto umum data lama",
                            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(220.dp))
                    }
                    Text("Bukan bukti fase atau penyelesaian kanonis.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                }
                Text("Tap di luar untuk tutup", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** Wire activity value → display label (avoids stringResource for the dynamic case). */
@Composable
private fun pembiasaanLabel(activity: String): String = when (activity) {
    ActivityType.TALIM -> stringResource(R.string.activity_talim)
    ActivityType.LARKAM -> stringResource(R.string.activity_larkam)
    ActivityType.SENAM -> stringResource(R.string.activity_senam)
    else -> stringResource(R.string.step_pembiasaan)
}

@Composable
private fun CenteredSpinner(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun CenteredMessage(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// --- Previews ------------------------------------------------------------

@Preview(showBackground = true, name = "History · Success")
@Composable
private fun HistorySuccessPreview() {
    FridaySTMTheme {
        HistoryContent(
            state = HistoryUiState.Success(
                AttendanceDayProjector.merge(listOf(
                    AttendanceRecord(
                        uid = "u1",
                        date = "2026-08-14",
                        grade = 11,
                        apel = ApelStamp(checkedIn = true, time = "06:12", lat = -6.87, lng = 107.54, valid = true),
                        pembiasaan = PembiasaanStamp(
                            activity = ActivityType.LARKAM,
                            checkedIn = true,
                            time = "06:45",
                            lat = -6.87,
                            lng = 107.54,
                            valid = false,
                        ),
                    ),
                    AttendanceRecord(
                        uid = "u1",
                        date = "2026-08-07",
                        grade = 11,
                        apel = ApelStamp(checkedIn = true, time = "06:05", lat = -6.87, lng = 107.54, valid = true),
                    ),
                ), emptyList()),
            ),
        )
    }
}

@Preview(showBackground = true, name = "History · Empty")
@Composable
private fun HistoryEmptyPreview() {
    FridaySTMTheme { HistoryContent(state = HistoryUiState.Empty) }
}
