package com.gynda.fridaystm.util.report

import com.gynda.fridaystm.data.model.StudentSummaryReport
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReportGeneratorTest {
    private val reports = listOf(StudentSummaryReport(
        studentName = "Budi", studentNis = "001", kelas = "X",
        totalHadir = 4, totalTerlambat = 2, totalComplete = 1, totalPartial = 2,
        totalLegacy = 3, totalNeedsReview = 4, totalAlfa = 5,
    ))
    private val date = Instant.parse("2026-09-03T17:00:00Z").toEpochMilli()

    @Test fun csvIncludesUnknownTimeInsteadOfSilentlyLosingRecordedDays() {
        val file = ExcelCsvReportGenerator.generate(RuntimeEnvironment.getApplication(), "X", date, date,
            listOf(reports.single().copy(totalUnknownTime = 7)))
        val lines = file.readLines()
        val headers = lines.first { it.startsWith("No,") }.split(',')
        val values = lines.first { it.startsWith("1,") }.split(',')
        assertEquals(headers.size, values.size)
        val cells = headers.zip(values).toMap()
        assertEquals("7", cells["Jam tidak tersedia/tidak valid"])
        assertEquals("4", cells["Tercatat sebelum 07:00"])
        assertEquals("2", cells["Tercatat mulai 07:00"])
        assertTrue(file.readText().contains("hanya jam HH:mm yang valid"))
    }

    @Test fun nativePdfFailureIsNotConvertedToFakeSuccess() {
        // Robolectric 4.13 does not implement native PdfDocument; real successful
        // generation/rendering is covered by PdfExportInstrumentedTest on Android.
        assertThrows(IllegalStateException::class.java) {
            PdfReportGenerator.generate(RuntimeEnvironment.getApplication(), "X", date, date, reports)
        }
    }

    @Test fun csvExportsLifecycleBreakdownAndTruthfulLabelsInSchoolTimezone() {
        val before = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            val file = ExcelCsvReportGenerator.generate(RuntimeEnvironment.getApplication(), "X", date, date, reports)
            val text = file.readText()
            assertTrue(file.name.contains("2026-09-04"))
            assertTrue(text.startsWith("\uFEFF"))
            assertTrue(text.contains("Lengkap,Parsial,Legacy,Perlu tinjauan"))
            assertTrue(text.contains("Belum tercatat"))
            assertTrue(text.contains("bukan verifikasi keterlambatan"))
            val headers = text.lineSequence().first { it.startsWith("No,") }.split(',')
            val values = text.lineSequence().first { it.startsWith("1,") }.split(',')
            val cells = headers.zip(values).toMap()
            assertEquals("1", cells["Lengkap"])
            assertEquals("2", cells["Parsial"])
            assertEquals("3", cells["Legacy"])
            assertEquals("4", cells["Perlu tinjauan"])
            File("build/outputs/report-evidence").mkdirs()
            file.copyTo(File("build/outputs/report-evidence/lifecycle.csv"), overwrite = true)
        } finally { TimeZone.setDefault(before) }
    }
}
