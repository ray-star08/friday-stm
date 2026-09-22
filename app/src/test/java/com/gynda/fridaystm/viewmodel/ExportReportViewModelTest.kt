package com.gynda.fridaystm.viewmodel

import android.content.Context
import com.gynda.fridaystm.data.model.ExportFormat
import com.gynda.fridaystm.data.model.StudentSummaryReport
import com.gynda.fridaystm.data.repository.ReportRepository
import org.robolectric.RuntimeEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Tests for ExportReportViewModel — verifies that the ViewModel correctly
 * orchestrates aggregation + file generation and exposes a valid Uri.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ExportReportViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeReportRepository(
        private val reports: List<StudentSummaryReport> = listOf(
            StudentSummaryReport(
                studentUid = "u1", studentName = "Budi", studentNis = "001",
                kelas = "XI RPL A", totalHadir = 5, totalTerlambat = 1, totalIzin = 0, totalSakit = 0, totalAlfa = 1,
                totalLarkamDistanceMeters = 3500.0, totalLarkamSessions = 2,
            ),
            StudentSummaryReport(
                studentUid = "u2", studentName = "Andi", studentNis = "002",
                kelas = "XI RPL A", totalHadir = 3, totalTerlambat = 0, totalIzin = 1, totalSakit = 0, totalAlfa = 2,
                totalLarkamDistanceMeters = 1200.0, totalLarkamSessions = 1,
            ),
        ),
    ) : ReportRepository {
        var lastKelas: String? = null
        var lastStart: Long? = null
        var lastEnd: Long? = null
        override suspend fun getRekapSummary(kelas: String, startDate: Long, endDate: Long): Result<List<StudentSummaryReport>> {
            lastKelas = kelas
            lastStart = startDate
            lastEnd = endDate
            return Result.success(reports)
        }
    }

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun nativePdfFailureReturnsErrorWithoutAnInvalidFileUri() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val repo = FakeReportRepository()
        val vm = ExportReportViewModel(reportRepository = repo, ioDispatcher = testDispatcher)

        // Use default class/dates but ensure format is PDF first
        vm.onFormatSelected(ExportFormat.PDF)
        vm.generateReport(context)
        advanceUntilIdle()

        // Real native PDF success and rendering runs in PdfExportInstrumentedTest.
        org.junit.Assert.assertNull(vm.uiState.value.generatedFileUri)
        assertNotNull(vm.uiState.value.errorMessage)
        org.junit.Assert.assertFalse(vm.uiState.value.isGenerating)
    }

    @Test
    fun generateReport_csv_createsFileAndReturnsValidUri() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val repo = FakeReportRepository()
        val vm = ExportReportViewModel(reportRepository = repo, ioDispatcher = testDispatcher)

        vm.onFormatSelected(ExportFormat.EXCEL_CSV)
        vm.generateReport(context)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertNotNull(state.generatedFileUri)
        assertTrue(state.generatedFileName!!.endsWith(".csv"))
        val file = File(context.cacheDir, state.generatedFileName!!)
        assertTrue(file.exists())
        assertTrue(file.length() > 0)
        // Check CSV header contains expected columns
        val content = file.readText(Charsets.UTF_8)
        assertTrue(content.contains("Nama"))
        assertTrue(content.contains("Budi"))
    }

    @Test
    fun onDateRangeSelected_validatesRange() = runTest {
        val vm = ExportReportViewModel(reportRepository = FakeReportRepository())
        val now = System.currentTimeMillis()
        val later = now + 86400000L
        vm.onDateRangeSelected(later, now) // invalid: start > end
        val state = vm.uiState.value
        assertNotNull(state.errorMessage)
    }
}
