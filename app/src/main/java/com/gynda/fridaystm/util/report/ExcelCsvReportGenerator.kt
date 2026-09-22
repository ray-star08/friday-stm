package com.gynda.fridaystm.util.report

import android.content.Context
import com.gynda.fridaystm.data.model.StudentSummaryReport
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Generates a CSV file compatible with Microsoft Excel.
 *
 * - UTF-8 with BOM (0xEF,0xBB,0xBF) so Excel detects UTF-8.
 * - Separator comma (,) with proper quoting for fields containing comma/quote/newline.
 * - Header info + column header + data rows.
 */
object ExcelCsvReportGenerator {

    private val dateFmt = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.forLanguageTag("id-ID"))

    fun generate(
        context: Context,
        kelas: String,
        startDate: Long,
        endDate: Long,
        reports: List<StudentSummaryReport>,
    ): File {
        val fileName = "rekap_${kelas.replace(" ", "_")}_${formatFileDate(startDate)}_${formatFileDate(endDate)}.csv"
        val file = File(context.cacheDir, fileName)
        file.outputStream().use { out ->
            // BOM for Excel UTF-8
            out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            val writer = out.bufferedWriter(Charsets.UTF_8)

            // Header info rows
            writer.write(csvEscape("Rekap Kehadiran & Larkam"))
            writer.newLine()
            writer.write("${csvEscape("Kelas")},${csvEscape(kelas)}")
            writer.newLine()
            writer.write("${csvEscape("Periode")},${csvEscape("${formatDate(startDate)} - ${formatDate(endDate)}")}")
            writer.newLine()
            writer.write("${csvEscape("Total Siswa")},${reports.size}")
            writer.newLine()
            writer.newLine()

            writer.write(csvEscape("Jam tampilan/capture, bukan verifikasi keterlambatan; hanya jam HH:mm yang valid masuk kelompok waktu; belum tercatat bukan alfa kalender resmi."))
            writer.newLine()

            // Column header
            val header = listOf("No", "Nama", "NIS", "Kelas", "Tercatat sebelum 07:00", "Tercatat mulai 07:00", "Jam tidak tersedia/tidak valid", "Izin", "Sakit", "Belum tercatat", "Lengkap", "Parsial", "Legacy", "Perlu tinjauan", "Total Larkam (km)", "Sessions")
            writer.write(header.joinToString(",") { csvEscape(it) })
            writer.newLine()

            // Data rows
            reports.forEachIndexed { idx, r ->
                val row = listOf(
                    (idx + 1).toString(),
                    r.studentName,
                    r.studentNis,
                    r.kelas,
                    r.totalHadir.toString(),
                    r.totalTerlambat.toString(),
                    r.totalUnknownTime.toString(),
                    r.totalIzin.toString(),
                    r.totalSakit.toString(),
                    r.totalAlfa.toString(),
                    r.totalComplete.toString(),
                    r.totalPartial.toString(),
                    r.totalLegacy.toString(),
                    r.totalNeedsReview.toString(),
                    String.format(Locale.US, "%.2f", r.totalLarkamDistanceMeters / 1000.0),
                    r.totalLarkamSessions.toString(),
                )
                writer.write(row.joinToString(",") { csvEscape(it) })
                writer.newLine()
            }
            writer.flush()
        }
        return file
    }

    private fun csvEscape(value: String): String {
        val needsQuote = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")
        return if (needsQuote) "\"${value.replace("\"", "\"\"")}\"" else value
    }

    private fun formatDate(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.of("Asia/Jakarta")).toLocalDate().format(dateFmt)

    private fun formatFileDate(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.of("Asia/Jakarta")).toLocalDate().toString()
}
