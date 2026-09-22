package com.gynda.fridaystm.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import com.gynda.fridaystm.data.model.StudentSummaryReport
import com.gynda.fridaystm.util.report.PdfReportGenerator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.time.Instant

/** Native output fixture; its text is asserted by the host PDF regression. */
class PdfUnknownTimeUiTest {
    @Test fun exportUnknownTimeFixture() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val date = Instant.parse("2026-09-03T17:00:00Z").toEpochMilli()
        val file = PdfReportGenerator.generate(context, "X", date, date, listOf(StudentSummaryReport(
            studentName = "Unknown Fixture", studentNis = "TIME-7", totalUnknownTime = 7, totalPartial = 7)))
        val dir = File(context.getExternalFilesDir(null), "ui-evidence").apply { mkdirs() }
        file.copyTo(File(dir, "unknown-time.pdf"), overwrite = true)
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
            assertEquals(1, renderer.pageCount)
            renderer.openPage(0).use { page ->
                val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                File(dir, "unknown-time.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
    }
}
