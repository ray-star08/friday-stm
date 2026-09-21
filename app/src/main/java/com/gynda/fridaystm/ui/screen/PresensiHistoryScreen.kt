package com.gynda.fridaystm.ui.screen

import android.content.Intent
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.gynda.fridaystm.R
import com.gynda.fridaystm.data.model.PresensiRecord
import com.gynda.fridaystm.ui.theme.FridaySTMTheme
import com.gynda.fridaystm.viewmodel.PresensiHistoryUiState
import com.gynda.fridaystm.viewmodel.PresensiHistoryViewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Presensi History — stateful holder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresensiHistoryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PresensiHistoryViewModel = viewModel(factory = PresensiHistoryViewModel.factory()),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.refreshing.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Riwayat Presensi") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PresensiHistoryContent(
                state = uiState,
                onRetry = viewModel::refresh,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun PresensiHistoryContent(
    state: PresensiHistoryUiState,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf<PresensiRecord?>(null) }
    when (state) {
        PresensiHistoryUiState.Loading -> Box(
            modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Memuat riwayat...", style = MaterialTheme.typography.bodyMedium)
            }
        }

        PresensiHistoryUiState.Empty -> Box(
            modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.DateRange,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Belum ada riwayat presensi",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Foto presensi watermark yang berhasil diunggah akan muncul di sini. Tap kartu untuk lihat detail & foto full.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        is PresensiHistoryUiState.Error -> Box(
            modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.Button(onClick = onRetry) {
                    Text("Muat Ulang")
                }
            }
        }

        is PresensiHistoryUiState.Success -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.records, key = { it.id.ifBlank { it.timestamp + it.imageUrl } }) { record ->
                PresensiItemCard(record = record, onClick = { selected = record })
            }
        }
    }
    selected?.let { rec ->
        PresensiDetailDialog(record = rec, onDismiss = { selected = null })
    }
}

@Composable
fun PresensiItemCard(
    record: PresensiRecord,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val formattedDate = rememberFormattedDate(record.timestamp)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = record.imageUrl,
                contentDescription = "Foto presensi ${record.studentName}",
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.mipmap.ic_launcher),
                error = painterResource(R.mipmap.ic_launcher),
                fallback = painterResource(R.mipmap.ic_launcher),
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        text = "Hadir",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                val locText = if (record.lat != null && record.lng != null) {
                    "%.4f, %.4f".format(record.lat, record.lng)
                } else {
                    record.let { "Lokasi tidak tersedia" }
                }
                // Prefer showing studentClass if lat/lng null? Spec says Koordinat/Nama Lokasi
                val locLine = if (record.lat != null) locText else if (record.studentClass.isNotBlank()) record.studentClass else locText
                Text(
                    text = locLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (record.studentName.isNotBlank()) {
                    Text(
                        text = "${record.studentName} • ${record.studentClass}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PresensiDetailDialog(
    record: PresensiRecord,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val formatted = rememberFormattedDate(record.timestamp)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Full image
                AsyncImage(
                    model = record.imageUrl,
                    contentDescription = "Foto presensi detail",
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(R.mipmap.ic_launcher),
                    error = painterResource(R.mipmap.ic_launcher),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(Modifier.height(16.dp))
                Text(formatted, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("${record.studentName} • ${record.studentClass}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(50)) {
                        Text("Hadir", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    if (record.lat != null && record.lng != null) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                        Text("%.6f, %.6f".format(record.lat, record.lng), style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text("Lokasi tidak tersedia", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (record.lat != null && record.lng != null) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        val uri = "geo:${record.lat},${record.lng}?q=${record.lat},${record.lng}".toUri()
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        try { context.startActivity(intent) } catch (_: Exception) {}
                    }) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Buka di Maps")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Tap di luar untuk tutup", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun rememberFormattedDate(iso: String): String {
    return try {
        val dt = LocalDateTime.parse(iso, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        dt.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM))
    } catch (_: Exception) {
        // Fallback: try date only or raw
        try {
            val dt = LocalDateTime.parse(iso)
            dt.format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))
        } catch (_: Exception) {
            iso.ifBlank { "-" }
        }
    }
}

// Previews
@Preview(showBackground = true, name = "Presensi History - Success")
@Composable
private fun PresensiHistorySuccessPreview() {
    FridaySTMTheme {
        PresensiHistoryContent(
            state = PresensiHistoryUiState.Success(
                listOf(
                    PresensiRecord(
                        id = "1",
                        userId = "u1",
                        timestamp = "2026-09-06T08:20:57",
                        imageUrl = "https://example.com/photo.jpg",
                        studentName = "Gynda Rayhan J.P.",
                        studentClass = "XI RPL A",
                        lat = -6.8921,
                        lng = 107.5432
                    ),
                    PresensiRecord(
                        id = "2",
                        userId = "u1",
                        timestamp = "2026-08-29T07:45:00",
                        imageUrl = "",
                        studentName = "Budi",
                        studentClass = "XI RPL A",
                        lat = null,
                        lng = null
                    )
                )
            )
        )
    }
}

@Preview(showBackground = true, name = "Presensi History - Empty")
@Composable
private fun PresensiHistoryEmptyPreview() {
    FridaySTMTheme { PresensiHistoryContent(state = PresensiHistoryUiState.Empty) }
}

@Preview(showBackground = true, name = "Presensi History - Loading")
@Composable
private fun PresensiHistoryLoadingPreview() {
    FridaySTMTheme { PresensiHistoryContent(state = PresensiHistoryUiState.Loading) }
}
