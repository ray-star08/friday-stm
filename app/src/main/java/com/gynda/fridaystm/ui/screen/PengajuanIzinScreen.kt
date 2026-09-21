package com.gynda.fridaystm.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.gynda.fridaystm.R
import com.gynda.fridaystm.data.model.IzinType
import com.gynda.fridaystm.ui.theme.FridaySTMTheme
import com.gynda.fridaystm.viewmodel.PengajuanIzinFormState
import com.gynda.fridaystm.viewmodel.PengajuanIzinUiState
import com.gynda.fridaystm.viewmodel.PengajuanIzinViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Pengajuan Izin/Sakit — stateful holder (SKILL.md §4.1).
 *
 * Memiliki [PengajuanIzinViewModel], mengoleksi form + status, dan meneruskan
 * navigasi sekali-tampil: [onSubmitSuccess] dipanggil tepat sekali saat status
 * menjadi SUCCESS (kembali ke Dashboard).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PengajuanIzinScreen(
    onBack: () -> Unit,
    onSubmitSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PengajuanIzinViewModel = viewModel(factory = PengajuanIzinViewModel.factory()),
) {
    val form by viewModel.formState.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val success = uiState is PengajuanIzinUiState.Success
    LaunchedEffect(success) {
        if (success) onSubmitSuccess()
    }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(stringResource(R.string.izin_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.izin_cd_back))
                    }
                },
            )
        },
        modifier = modifier,
    ) { padding ->
        PengajuanIzinContent(
            form = form,
            uiState = uiState,
            onTipeChange = viewModel::onTipeChange,
            onStartDateChange = viewModel::onStartDateChange,
            onEndDateChange = viewModel::onEndDateChange,
            onAlasanChange = viewModel::onAlasanChange,
            onProofSelected = viewModel::onProofSelected,
            onProofCleared = viewModel::onProofCleared,
            onSubmit = viewModel::onSubmit,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}

/**
 * Pengajuan Izin/Sakit — stateless content. Murni render dari [form] +
 * [uiState]; tanpa ViewModel sehingga previewable & testable (SKILL.md §4.1).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PengajuanIzinContent(
    form: PengajuanIzinFormState,
    uiState: PengajuanIzinUiState,
    onTipeChange: (IzinType) -> Unit,
    onStartDateChange: (Long?) -> Unit,
    onEndDateChange: (Long?) -> Unit,
    onAlasanChange: (String) -> Unit,
    onProofSelected: (String, ByteArray) -> Unit,
    onProofCleared: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isLoading = uiState is PengajuanIzinUiState.Loading

    // Document/image picker — GetContent("image/*") mencakup foto surat
    // (surat dokter / surat orang tua) di semua API level yang didukung.
    val pickProof = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val bytes = context.contentResolver.openInputStream(uri)
                    ?.use { it.readBytes() }
                if (bytes != null && bytes.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        onProofSelected(uri.toString(), bytes)
                    }
                }
            }
        }
    }

    var showStartPicker by rememberSaveable { mutableStateOf(false) }
    var showEndPicker by rememberSaveable { mutableStateOf(false) }

    if (showStartPicker) {
        IzinDatePickerDialog(
            initialMillis = form.startDateMillis,
            onDismiss = { showStartPicker = false },
            onConfirm = {
                onStartDateChange(it)
                showStartPicker = false
            },
        )
    }
    if (showEndPicker) {
        IzinDatePickerDialog(
            initialMillis = form.endDateMillis,
            onDismiss = { showEndPicker = false },
            onConfirm = {
                onEndDateChange(it)
                showEndPicker = false
            },
        )
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // --- Tipe: Sakit / Izin ---
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.izin_tipe_label),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = form.tipe == IzinType.SAKIT,
                    onClick = { onTipeChange(IzinType.SAKIT) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    enabled = !isLoading,
                ) {
                    Text(stringResource(R.string.izin_tipe_sakit))
                }
                SegmentedButton(
                    selected = form.tipe == IzinType.IZIN,
                    onClick = { onTipeChange(IzinType.IZIN) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    enabled = !isLoading,
                ) {
                    Text(stringResource(R.string.izin_tipe_izin))
                }
            }
        }

        // --- Rentang tanggal ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DateField(
                label = stringResource(R.string.izin_start_label),
                millis = form.startDateMillis,
                enabled = !isLoading,
                onClick = { showStartPicker = true },
                modifier = Modifier.weight(1f),
            )
            DateField(
                label = stringResource(R.string.izin_end_label),
                millis = form.endDateMillis,
                enabled = !isLoading,
                onClick = { showEndPicker = true },
                modifier = Modifier.weight(1f),
            )
        }

        // --- Alasan ---
        OutlinedTextField(
            value = form.alasan,
            onValueChange = onAlasanChange,
            label = { Text(stringResource(R.string.izin_alasan_label)) },
            placeholder = { Text(stringResource(R.string.izin_alasan_hint)) },
            supportingText = {
                Text(
                    stringResource(
                        R.string.izin_alasan_counter,
                        form.alasan.trim().length,
                        PengajuanIzinViewModel.MIN_ALASAN_LENGTH,
                    ),
                )
            },
            isError = form.alasan.isNotBlank() &&
                form.alasan.trim().length < PengajuanIzinViewModel.MIN_ALASAN_LENGTH,
            minLines = 3,
            maxLines = 6,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
        )

        // --- Bukti surat ---
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.izin_proof_label),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.izin_proof_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (form.proofUri == null) {
                OutlinedButton(
                    onClick = { pickProof.launch("image/*") },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.izin_proof_pick))
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = form.proofUri,
                        contentDescription = stringResource(R.string.izin_cd_proof_preview),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(16.dp)),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { pickProof.launch("image/*") },
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.izin_proof_change))
                    }
                    TextButton(
                        onClick = onProofCleared,
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.izin_proof_remove))
                    }
                }
            }
        }

        // --- Error sekali-tampil ---
        if (uiState is PengajuanIzinUiState.Error) {
            Text(
                text = (uiState as PengajuanIzinUiState.Error).message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // --- Kirim ---
        Button(
            onClick = onSubmit,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.izin_submit_loading))
            } else {
                Text(stringResource(R.string.izin_submit))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IzinDatePickerDialog(
    initialMillis: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.selectedDateMillis) }) {
                Text(stringResource(R.string.izin_date_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.izin_date_cancel))
            }
        },
        modifier = modifier,
    ) {
        DatePicker(state = state)
    }
}

@Composable
private fun DateField(
    label: String,
    millis: Long?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                Icons.Filled.DateRange,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = millis?.let { formatDisplayDate(it) } ?: "—",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Epoch-millis → `dd MMM yyyy` untuk tampilan; pure `java.time`. */
private fun formatDisplayDate(epochMillis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
    return Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(formatter)
}

// --- Previews ------------------------------------------------------------

@Preview(showBackground = true, name = "Izin · Empty form")
@Composable
private fun PengajuanIzinEmptyPreview() {
    FridaySTMTheme {
        PengajuanIzinContent(
            form = PengajuanIzinFormState(),
            uiState = PengajuanIzinUiState.Idle,
            onTipeChange = {},
            onStartDateChange = {},
            onEndDateChange = {},
            onAlasanChange = {},
            onProofSelected = { _, _ -> },
            onProofCleared = {},
            onSubmit = {},
        )
    }
}

@Preview(showBackground = true, name = "Izin · Filled + error")
@Composable
private fun PengajuanIzinErrorPreview() {
    FridaySTMTheme {
        PengajuanIzinContent(
            form = PengajuanIzinFormState(
                tipe = IzinType.SAKIT,
                alasan = "Demam",
                proofUri = null,
            ),
            uiState = PengajuanIzinUiState.Error("Alasan minimal 10 karakter."),
            onTipeChange = {},
            onStartDateChange = {},
            onEndDateChange = {},
            onAlasanChange = {},
            onProofSelected = { _, _ -> },
            onProofCleared = {},
            onSubmit = {},
        )
    }
}
