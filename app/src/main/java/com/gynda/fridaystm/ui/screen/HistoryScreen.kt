package com.gynda.fridaystm.ui.screen

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.gynda.fridaystm.R
import com.gynda.fridaystm.data.model.ApelStamp
import com.gynda.fridaystm.data.model.AttendanceRecord
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
    when (state) {
        HistoryUiState.Loading -> CenteredSpinner(modifier)
        HistoryUiState.Empty -> CenteredMessage(stringResource(R.string.history_empty), modifier)
        is HistoryUiState.Error -> CenteredMessage(stringResource(state.messageResId), modifier)
        is HistoryUiState.Success -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.records, key = { it.docId }) { record ->
                AttendanceHistoryCard(record)
            }
        }
    }
}

/**
 * One day's attendance summary: date header plus a row per completed phase
 * (Apel / Pembiasaan) showing time, status, selfie thumbnail and coordinates.
 */
@Composable
private fun AttendanceHistoryCard(
    record: AttendanceRecord,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
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
                listOf(
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
                ),
            ),
        )
    }
}

@Preview(showBackground = true, name = "History · Empty")
@Composable
private fun HistoryEmptyPreview() {
    FridaySTMTheme { HistoryContent(state = HistoryUiState.Empty) }
}
