package com.gynda.fridaystm.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.gynda.fridaystm.R
import com.gynda.fridaystm.data.model.PresensiRecord
import com.gynda.fridaystm.ui.component.EmptyState
import com.gynda.fridaystm.ui.component.ErrorState
import com.gynda.fridaystm.ui.theme.Spacing
import com.gynda.fridaystm.viewmodel.PresensiHistoryUiState
import com.gynda.fridaystm.viewmodel.PresensiHistoryViewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Galeri selfie: grid 3 kolom dari riwayat presensi. Read-only — memakai
 * [PresensiHistoryViewModel] yang sama, tanpa query/endpoint baru.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PresensiHistoryViewModel = viewModel(factory = PresensiHistoryViewModel.factory()),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var preview by remember { mutableStateOf<PresensiRecord?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.gallery_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val state = uiState) {
                PresensiHistoryUiState.Loading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                PresensiHistoryUiState.Empty -> EmptyState(
                    icon = Icons.Filled.Face,
                    title = stringResource(R.string.gallery_empty_title),
                    body = stringResource(R.string.gallery_empty_body),
                    modifier = Modifier.align(Alignment.Center),
                )

                is PresensiHistoryUiState.Error -> ErrorState(
                    message = state.message,
                    onRetry = viewModel::refresh,
                    modifier = Modifier.padding(Spacing.s16),
                )

                is PresensiHistoryUiState.Success -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(Spacing.s12),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s8),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s8),
                ) {
                    itemsIndexed(
                        items = state.records,
                        key = { index, record -> record.id.ifBlank { "row_$index" } },
                    ) { _, record ->
                        GalleryCell(record = record, onClick = { preview = record })
                    }
                }
            }
        }
    }

    preview?.let { record ->
        Dialog(
            onDismissRequest = { preview = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { preview = null },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = record.imageUrl,
                    contentDescription = stringResource(R.string.gallery_cd_preview),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun GalleryCell(record: PresensiRecord, onClick: () -> Unit) {
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = record.imageUrl,
            contentDescription = stringResource(R.string.gallery_cd_photo, displayTime(record.timestamp)),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.align(Alignment.BottomStart).padding(Spacing.s4),
        ) {
            Text(
                text = displayTime(record.timestamp),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/** `2026-09-06T08:20:57` → `6 Sep 08:20`; timestamp rusak tampil apa adanya. */
private fun displayTime(timestamp: String): String = try {
    LocalDateTime.parse(timestamp)
        .format(DateTimeFormatter.ofPattern("d MMM HH:mm", Locale("id", "ID")))
} catch (_: Exception) {
    timestamp
}
