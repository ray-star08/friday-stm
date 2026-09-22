package com.gynda.fridaystm.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import com.gynda.fridaystm.data.model.StudentSummaryReport
import com.gynda.fridaystm.util.report.PdfReportGenerator
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Real Android PDF renderer, not Robolectric's fake native PdfDocument. */
class PdfExportInstrumentedTest {
    @Test fun nativePdfContainsAllPagesAndCanRender() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val reports = (1..35).map { index -> StudentSummaryReport(
            studentName = "Siswa Contoh $index", studentNis = "DEMO-$index", kelas = "XI A",
            totalHadir = 4, totalTerlambat = 2, totalComplete = 1, totalPartial = 2,
            totalLegacy = 3, totalNeedsReview = 4, totalAlfa = 5,
        ) }
        val date = Instant.parse("2026-09-03T17:00:00Z").toEpochMilli()
        val file = PdfReportGenerator.generate(context, "XI A", date, date, reports)
        val evidence = File(context.getExternalFilesDir(null), "ui-evidence").apply { mkdirs() }
        file.copyTo(File(evidence, "lifecycle.pdf"), overwrite = true)
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
            assertEquals("All fixture pages must survive", 3, renderer.pageCount)
            val rows = JSONArray()
            reports.forEachIndexed { index, report ->
                val cells = listOf((index + 1).toString(), report.studentName, report.studentNis,
                    report.totalHadir.toString(), report.totalTerlambat.toString(), report.totalIzin.toString(),
                    report.totalSakit.toString(), report.totalAlfa.toString(),
                    String.format(Locale.US, "%.2f", report.totalLarkamDistanceMeters / 1000.0))
                val details = listOf("Lengkap: ${report.totalComplete} | Parsial: ${report.totalPartial} | Legacy: ${report.totalLegacy} | Perlu tinjauan: ${report.totalNeedsReview}",
                    "Jam tidak tersedia/tidak valid: ${report.totalUnknownTime} | Sesi Larkam: ${report.totalLarkamSessions}")
                rows.put(JSONObject().put("cells", JSONArray(cells)).put("details", JSONArray(details)))
            }
            File(evidence, "lifecycle-expected.json").writeText(
                JSONObject().put("pages", 3).put("rows", rows).toString(2))
            for (index in 0 until renderer.pageCount) {
                renderer.openPage(index).use { page ->
                    val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    File(evidence, "report-page-$index.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
            }
        }
    }
}
