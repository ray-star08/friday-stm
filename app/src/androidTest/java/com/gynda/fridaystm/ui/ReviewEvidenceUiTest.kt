package com.gynda.fridaystm.ui

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.gynda.fridaystm.data.model.*
import com.gynda.fridaystm.data.repository.ReportRepository
import com.gynda.fridaystm.ui.screen.ExportReportContent
import com.gynda.fridaystm.ui.screen.HistoryContent
import com.gynda.fridaystm.ui.theme.FridaySTMTheme
import com.gynda.fridaystm.viewmodel.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.TimeZone

/** Offline review regressions: merged source data and the actual Material date picker. */
class ReviewEvidenceUiTest {
    @get:Rule val compose = createComposeRule()
    private val originalZone = TimeZone.getDefault()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val end = Instant.parse("2026-09-03T17:00:00Z").toEpochMilli()

    @Composable private fun TestTheme(content: @Composable () -> Unit) {
        val args = InstrumentationRegistry.getArguments()
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, args.getString("fontScale", "1.0").toFloat())) {
            FridaySTMTheme(darkTheme = args.getString("darkTheme") == "true", content = content)
        }
    }

    @After fun cleanupAndCapture() {
        try {
            val dir = File(context.getExternalFilesDir(null), "ui-evidence").apply { mkdirs() }
            val roots = compose.onAllNodes(isRoot())
            roots.fetchSemanticsNodes().indices.forEach { index ->
                File(dir, "review-${System.nanoTime()}-$index.png").outputStream().use {
                    roots[index].captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                File(dir, "semantics-${System.nanoTime()}-$index.txt").writeText(roots[index].printToString())
            }
            if (compose.onAllNodesWithTag("canonical-attendance-detail").fetchSemanticsNodes().isNotEmpty()) {
                File(dir, "mixed-detail-${System.nanoTime()}.png").outputStream().use {
                    compose.onNodeWithTag("canonical-attendance-detail").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        } finally { TimeZone.setDefault(originalZone) }
    }

    private class Reports : ReportRepository {
        @Volatile var query: Pair<Long, Long>? = null
        override suspend fun getRekapSummary(kelas: String, startDate: Long, endDate: Long): Result<List<StudentSummaryReport>> {
            query = startDate to endDate
            return Result.success(emptyList())
        }
    }

    private fun export(zone: String, reports: Reports = Reports()): ExportReportViewModel {
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
        val vm = ExportReportViewModel(reports, clock = Clock.fixed(Instant.parse("2026-09-03T17:30:00Z"), ZoneId.of(zone)))
        compose.setContent { TestTheme { Surface {
            val state by vm.uiState.collectAsState()
            ExportReportContent(state, vm::onClassSelected, vm::onDateRangeSelected, vm::onFormatSelected,
                { vm.generateReport(context) }, {}, {})
        } } }
        return vm
    }

    @Test fun displayedDateInLosAngelesMatchesRepositorySchoolDay() {
        val reports = Reports()
        export("America/Los_Angeles", reports)
        compose.onNodeWithText("04 Sep 2026").assertIsDisplayed()
        compose.onNodeWithText("CSV (Excel)").performScrollTo().performClick()
        compose.onNodeWithText("Generate & Download Report").performScrollTo().performClick()
        compose.waitUntil(10_000) { reports.query != null }
        assertEquals("2026-09-04", Instant.ofEpochMilli(reports.query!!.second).atZone(ZoneId.of("Asia/Jakarta")).toLocalDate().toString())
    }

    @Test fun pickerInitiallySelectsTheDisplayedSchoolDay() {
        val vm = export("Asia/Makassar")
        compose.onNodeWithText("04 Sep 2026").performClick()
        compose.onNodeWithText("OK").performClick()
        compose.runOnIdle { assertEquals(end, vm.uiState.value.endDate) }
        compose.onNodeWithText("04 Sep 2026").assertIsDisplayed()
    }

    @Test fun pickerSelectionBecomesSchoolMidnightAndQueriesSelectedDay() {
        val reports = Reports()
        val vm = export("Asia/Makassar", reports)
        compose.onNodeWithText("04 Sep 2026").performClick()
        compose.onNodeWithText("Saturday, September 5, 2026").performClick()
        compose.onNodeWithText("OK").performClick()
        compose.runOnIdle { assertEquals(Instant.parse("2026-09-04T17:00:00Z").toEpochMilli(), vm.uiState.value.endDate) }
        compose.onNodeWithText("05 Sep 2026").assertIsDisplayed()
        compose.onNodeWithText("CSV (Excel)").performScrollTo().performClick()
        compose.onNodeWithText("Generate & Download Report").performScrollTo().performClick()
        compose.waitUntil(10_000) { reports.query != null }
        assertEquals("2026-09-05", Instant.ofEpochMilli(reports.query!!.second).atZone(ZoneId.of("Asia/Jakarta")).toLocalDate().toString())
    }

    @Test fun partialCanonicalRetainsGenericLegacyProofWithoutUpgrade() = mixedProof("incomplete", AttendanceDayStatus.PARTIAL, "Sebagian")
    @Test fun flaggedCanonicalRetainsGenericLegacyProofWithoutUpgrade() = mixedProof("flagged", AttendanceDayStatus.NEEDS_REVIEW, "Perlu ditinjau")

    private fun mixedProof(status: String, expected: AttendanceDayStatus, label: String) {
        val photo = File(context.cacheDir, "generic-proof.png")
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val day = AttendanceDayProjector.merge(listOf(AttendanceRecord(uid = "synthetic", date = "2026-09-18", status = status,
            pembiasaan = PembiasaanStamp(checkedIn = true, valid = true, time = ""))),
            listOf(PresensiRecord(userId = "synthetic", timestamp = "2026-09-18T06:43:00", imageUrl = photo.toURI().toString()))).single()
        assertEquals(expected, day.status)
        assertEquals("", day.time) // Generic capture must not fill canonical phase time.
        compose.setContent { TestTheme { Surface { HistoryContent(HistoryUiState.Success(listOf(day))) } } }
        compose.onNodeWithText(label).assertIsDisplayed()
        compose.onNodeWithText("2026-09-18").performClick()
        val dialog = hasAnyAncestor(hasTestTag("canonical-attendance-detail"))
        compose.onNode(hasText("Bukti umum data lama") and dialog).performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("Jam capture (WIB): 06:43") and dialog).performScrollTo().assertIsDisplayed()
        compose.onNode(hasContentDescription("Bukti foto umum data lama") and dialog).performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("Bukan bukti fase atau penyelesaian kanonis.") and dialog).performScrollTo().assertIsDisplayed()
        compose.onNode(hasText(label) and dialog).performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("Lengkap") and dialog).assertDoesNotExist()
        compose.onNode(hasText("Check-out", substring = true) and dialog).assertDoesNotExist()
    }
}
