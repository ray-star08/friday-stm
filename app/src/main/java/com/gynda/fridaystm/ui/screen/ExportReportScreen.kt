package com.gynda.fridaystm.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gynda.fridaystm.R
import com.gynda.fridaystm.data.model.ExportFormat
import com.gynda.fridaystm.ui.theme.Elevation
import com.gynda.fridaystm.ui.theme.Radius
import com.gynda.fridaystm.ui.theme.Spacing
import com.gynda.fridaystm.util.TeacherDashboardDefaults
import com.gynda.fridaystm.viewmodel.ExportReportViewModel
import com.gynda.fridaystm.util.SchoolDates
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Export Rekap Report — stateful holder.
 *
 * Fungsionalitas dasar: pilih kelas, rentang tanggal, format PDF/CSV,
 * generate di Dispatchers.IO, expose FileProvider Uri, dialog buka/bagikan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("ModifierParameter") // SKILL §4.2: modifier last; lint wants modifier first optional.
@Composable
fun ExportReportScreen(
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: ExportReportViewModel = viewModel(factory = ExportReportViewModel.factory()),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHost.showSnackbar(it)
            viewModel.onErrorConsumed()
        }
    }

    // When a file is generated, show dialog with open/share
    var showGeneratedDialog by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.generatedFileUri) {
        if (state.generatedFileUri != null) showGeneratedDialog = true
    }

    if (showGeneratedDialog && state.generatedFileUri != null) {
        AlertDialog(
            onDismissRequest = {
                showGeneratedDialog = false
                viewModel.onGeneratedConsumed()
            },
            title = { Text(stringResource(R.string.export_dialog_title)) },
            text = { Text(stringResource(R.string.export_dialog_body, state.generatedFileName.orEmpty())) },
            confirmButton = {
                TextButton(onClick = {
                    openFile(context, state.generatedFileUri!!, state.exportFormat)
                    showGeneratedDialog = false
                }) { Text(stringResource(R.string.export_open_file)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        shareFile(context, state.generatedFileUri!!)
                        showGeneratedDialog = false
                    }) { Text(stringResource(R.string.export_dialog_share)) }
                    TextButton(onClick = {
                        showGeneratedDialog = false
                        viewModel.onGeneratedConsumed()
                    }) { Text(stringResource(R.string.export_dialog_close)) }
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.export_title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        modifier = modifier,
    ) { padding ->
        ExportReportContent(
            state = state,
            onClassSelected = viewModel::onClassSelected,
            onDateRangeSelected = viewModel::onDateRangeSelected,
            onFormatSelected = viewModel::onFormatSelected,
            onGenerate = { viewModel.generateReport(context) },
            onOpenFile = { uri -> openFile(context, uri, state.exportFormat) },
            onShareFile = { uri -> shareFile(context, uri) },
            modifier = Modifier.padding(padding),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportReportContent(
    state: com.gynda.fridaystm.viewmodel.ExportReportUiState,
    onClassSelected: (String) -> Unit,
    onDateRangeSelected: (Long, Long) -> Unit,
    onFormatSelected: (ExportFormat) -> Unit,
    onGenerate: () -> Unit,
    onOpenFile: (Uri) -> Unit,
    onShareFile: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showStartPicker by rememberSaveable { mutableStateOf(false) }
    var showEndPicker by rememberSaveable { mutableStateOf(false) }

    if (showStartPicker) {
        ReportDatePickerDialog(
            initialMillis = state.startDate,
            onDismiss = { showStartPicker = false },
            onConfirm = { millis ->
                val newStart = millis ?: state.startDate
                onDateRangeSelected(newStart, state.endDate)
                showStartPicker = false
            },
        )
    }
    if (showEndPicker) {
        ReportDatePickerDialog(
            initialMillis = state.endDate,
            onDismiss = { showEndPicker = false },
            onConfirm = { millis ->
                val newEnd = millis ?: state.endDate
                onDateRangeSelected(state.startDate, newEnd)
                showEndPicker = false
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.s16),
        verticalArrangement = Arrangement.spacedBy(Spacing.s16),
    ) {
        // Kelas selector
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = state.selectedClass,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.teacher_filter_kelas)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                TeacherDashboardDefaults.AVAILABLE_CLASSES.forEach { kelas ->
                    DropdownMenuItem(text = { Text(kelas) }, onClick = { onClassSelected(kelas); expanded = false })
                }
            }
        }

        // Date range pickers
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s12)) {
            DateFieldCard(
                label = stringResource(R.string.export_start),
                millis = state.startDate,
                onClick = { showStartPicker = true },
                modifier = Modifier.weight(1f),
            )
            DateFieldCard(
                label = stringResource(R.string.export_end),
                millis = state.endDate,
                onClick = { showEndPicker = true },
                modifier = Modifier.weight(1f),
            )
        }

        // Format segmented — secondaryContainer untuk selected (M3 tonal)
        Text(stringResource(R.string.export_format), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = state.exportFormat == ExportFormat.PDF,
                onClick = { onFormatSelected(ExportFormat.PDF) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) { Text("PDF") }
            SegmentedButton(
                selected = state.exportFormat == ExportFormat.EXCEL_CSV,
                onClick = { onFormatSelected(ExportFormat.EXCEL_CSV) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) { Text("CSV (Excel)") }
        }

        // Generate button — animateContentSize for morph
        Button(
            onClick = onGenerate,
            enabled = !state.isGenerating,
            modifier = Modifier.fillMaxWidth().animateContentSize(),
        ) {
            if (state.isGenerating) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(Spacing.s8))
                Text(stringResource(R.string.export_generating))
            } else {
                Text(stringResource(R.string.export_generate_cta))
            }
        }

        // When generated, show quick actions inline as well
        if (state.generatedFileUri != null) {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.m),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = Elevation.card),
            ) {
                Column(Modifier.fillMaxWidth().padding(Spacing.s12), verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                    Text(stringResource(R.string.export_file_ready, state.generatedFileName.orEmpty()), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                        OutlinedButton(onClick = { onOpenFile(state.generatedFileUri) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.export_open_file)) }
                        OutlinedButton(onClick = { onShareFile(state.generatedFileUri) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.export_share_file)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DateFieldCard(label: String, millis: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.DateRange, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(formatDisplayDate(millis), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportDatePickerDialog(initialMillis: Long, onDismiss: () -> Unit, onConfirm: (Long?) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = SchoolDates.toPickerMillis(initialMillis))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onConfirm(state.selectedDateMillis?.let(SchoolDates::fromPickerMillis)) }) { Text(stringResource(R.string.izin_date_ok)) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.izin_date_cancel)) } },
    ) { DatePicker(state = state) }
}

private fun formatDisplayDate(millis: Long): String {
    return try {
        SchoolDates.date(millis)
            .format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.forLanguageTag("id-ID")))
    } catch (_: Exception) { "-" }
}

private fun openFile(context: android.content.Context, uri: Uri, format: ExportFormat) {
    val mime = when (format) {
        ExportFormat.PDF -> "application/pdf"
        ExportFormat.EXCEL_CSV -> "text/csv"
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Buka File"))
    } catch (_: Exception) {
        // fallback: try ACTION_VIEW without chooser
        try { context.startActivity(intent) } catch (_: Exception) {}
    }
}

private fun shareFile(context: android.content.Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "*/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Bagikan File"))
    } catch (_: Exception) {}
}
