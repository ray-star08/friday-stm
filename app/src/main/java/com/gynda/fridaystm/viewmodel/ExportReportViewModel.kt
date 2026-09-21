package com.gynda.fridaystm.viewmodel

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gynda.fridaystm.data.model.ExportFormat
import com.gynda.fridaystm.data.model.RekapReportFilter
import com.gynda.fridaystm.data.repository.FirebaseReportRepository
import com.gynda.fridaystm.data.repository.ReportRepository
import com.gynda.fridaystm.util.TeacherDashboardDefaults
import com.gynda.fridaystm.util.report.ExcelCsvReportGenerator
import com.gynda.fridaystm.util.report.PdfReportGenerator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId

data class ExportReportUiState(
    val selectedClass: String = TeacherDashboardDefaults.DEFAULT_CLASS,
    val startDate: Long = defaultStartDate(),
    val endDate: Long = defaultEndDate(),
    val exportFormat: ExportFormat = ExportFormat.PDF,
    val isGenerating: Boolean = false,
    val generatedFileUri: Uri? = null,
    val generatedFileName: String? = null,
    val errorMessage: String? = null,
) {
    companion object {
        fun defaultStartDate(): Long {
            // 7 days ago at 00:00
            val now = java.time.LocalDate.now()
            return now.minusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        fun defaultEndDate(): Long {
            val now = java.time.LocalDate.now()
            return now.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
    }
}

class ExportReportViewModel(
    private val reportRepository: ReportRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val uriProvider: (Context, File) -> Uri = { ctx, file ->
        try {
            FileProvider.getUriForFile(ctx.applicationContext, "${ctx.packageName}.fileprovider", file)
        } catch (_: Exception) {
            Uri.fromFile(file)
        }
    },
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExportReportUiState())
    val uiState: StateFlow<ExportReportUiState> = _uiState.asStateFlow()

    fun onClassSelected(kelas: String) {
        if (kelas.isNotBlank()) _uiState.update { it.copy(selectedClass = kelas, errorMessage = null) }
    }

    fun onDateRangeSelected(start: Long, end: Long) {
        if (start <= end) _uiState.update { it.copy(startDate = start, endDate = end, errorMessage = null) }
        else _uiState.update { it.copy(errorMessage = "Tanggal mulai harus <= tanggal selesai") }
    }

    fun onFormatSelected(format: ExportFormat) {
        _uiState.update { it.copy(exportFormat = format) }
    }

    /**
     * Core generation: fetch aggregated data on IO, then call the appropriate
     * generator (PDF via PdfDocument/Canvas, CSV via BufferedWriter) and
     * expose a FileProvider Uri.
     *
     * The caller (UI) can then trigger ACTION_VIEW or ACTION_SEND.
     */
    fun generateReport(context: Context) {
        val state = _uiState.value
        if (state.selectedClass.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Pilih kelas terlebih dahulu") }
            return
        }
        if (state.startDate > state.endDate) {
            _uiState.update { it.copy(errorMessage = "Rentang tanggal tidak valid") }
            return
        }
        _uiState.update { it.copy(isGenerating = true, errorMessage = null, generatedFileUri = null) }
        viewModelScope.launch {
            try {
                val result = withContext(ioDispatcher) {
                    val summaryResult = reportRepository.getRekapSummary(
                        kelas = state.selectedClass,
                        startDate = state.startDate,
                        endDate = state.endDate,
                    )
                    val reports = summaryResult.getOrThrow()
                    val file = when (state.exportFormat) {
                        ExportFormat.PDF -> PdfReportGenerator.generate(
                            context = context.applicationContext,
                            kelas = state.selectedClass,
                            startDate = state.startDate,
                            endDate = state.endDate,
                            reports = reports,
                        )
                        ExportFormat.EXCEL_CSV -> ExcelCsvReportGenerator.generate(
                            context = context.applicationContext,
                            kelas = state.selectedClass,
                            startDate = state.startDate,
                            endDate = state.endDate,
                            reports = reports,
                        )
                    }
                    // Validate non-empty
                    require(file.exists() && file.length() > 0) { "Generated file is empty" }
                    val uri = uriProvider(context.applicationContext, file)
                    file to uri
                }
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        generatedFileUri = result.second,
                        generatedFileName = result.first.name,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isGenerating = false, errorMessage = e.message ?: "Gagal generate laporan") }
            }
        }
    }

    fun onErrorConsumed() { _uiState.update { it.copy(errorMessage = null) } }
    fun onGeneratedConsumed() { _uiState.update { it.copy(generatedFileUri = null) } }

    companion object {
        fun factory(
            reportRepository: ReportRepository = FirebaseReportRepository(),
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ExportReportViewModel(reportRepository) }
        }
    }
}
