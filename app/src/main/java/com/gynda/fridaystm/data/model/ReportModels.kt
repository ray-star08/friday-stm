package com.gynda.fridaystm.data.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * StudentSummaryReport — aggregated per-student for a class & date range.
 *
 * Mirrors spec: totalHadir / totalTerlambat distinguished by presensi time cutoff
 * (07:00 WIB), totalIzin / totalSakit from izin_records tipe, totalAlfa as
 * remaining expected days, larkam distance summed in meters.
 */
data class StudentSummaryReport(
    val studentUid: String = "",
    val studentName: String = "",
    val studentNis: String = "",
    val kelas: String = "",
    val totalHadir: Int = 0,
    val totalTerlambat: Int = 0,
    val totalIzin: Int = 0,
    val totalSakit: Int = 0,
    val totalAlfa: Int = 0,
    val totalLarkamDistanceMeters: Double = 0.0,
    val totalLarkamSessions: Int = 0,
)

/**
 * Filter for report generation.
 *
 * startDate / endDate are epoch millis at 00:00 local (inclusive).
 * The repository normalizes them to LocalDate for Firestore filtering.
 */
data class RekapReportFilter(
    val kelas: String = "",
    val startDate: Long = 0L,
    val endDate: Long = 0L,
    val format: ExportFormat = ExportFormat.PDF,
)

enum class ExportFormat {
    PDF,
    EXCEL_CSV,
}

/**
 * Pure aggregation logic — unit-testable without Firestore/Android.
 *
 * - Presensi: timestamp ISO "yyyy-MM-ddTHH:mm:ss" ; time < 07:00 => HADIR else TERLAMBAT.
 * - Izin: tipe IZIN/SAKIT ; counts within range (startDate–endDate inclusive, overlap).
 * - Larkam: sum distanceInMeters / distanceMeters / distanceKm*1000 from larkam_records.
 * - Alfa: distinctAttendanceDates (unique presensi dates for the class in range)
 *   minus hadir/terlambat/izin/sakit per student, floored at 0. If no attendance dates
 *   in range, alfa is 0 (no expected days).
 */
object ReportAggregator {

    private val lateFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private const val LATE_CUTOFF = "07:00"

    /**
     * Aggregate presensi + izin + larkam per student.
     *
     * @param users all students in the class (for roster & alfa denominator)
     * @param presensi all presensi_records for the class within [startDate, endDate]
     * @param izin all izin_records for the class overlapping the range
     * @param larkam all larkam_records for the class within range
     * @param startDate inclusive epoch millis
     * @param endDate inclusive epoch millis
     */
    fun aggregate(
        users: List<User>,
        presensi: List<PresensiRecord>,
        izin: List<IzinRecord>,
        larkam: List<LarkamRecord>,
        startDate: Long,
        endDate: Long,
    ): List<StudentSummaryReport> {
        val startLocal = Instant.ofEpochMilli(startDate).atZone(ZoneId.systemDefault()).toLocalDate()
        val endLocal = Instant.ofEpochMilli(endDate).atZone(ZoneId.systemDefault()).toLocalDate()

        val dailyPresensi = earliestDailyPresensi(presensi)
        // Preserve the existing observed-days denominator; calendar policy is separate.
        val distinctAttendanceDates = dailyPresensi.mapNotNull { extractDate(it.timestamp) }.toSet().size

        // Group by userId for O(1) lookup
        val presensiByUser = dailyPresensi.groupBy { it.userId }
        val izinByUser = izin.filter { it.status == com.gynda.fridaystm.util.IzinStatus.APPROVED }.groupBy { it.userId }
        val larkamByUser = larkam.groupBy { it.userId }

        return users.map { user ->
            val pList = presensiByUser[user.uid].orEmpty()
            var hadir = 0
            var terlambat = 0
            for (rec in pList) {
                if (isLate(rec.timestamp)) terlambat++ else hadir++
            }

            val iList = izinByUser[user.uid].orEmpty()
            // Count izin/sakit records that overlap the requested range (already filtered, but double-check)
            var totalIzin = 0
            var totalSakit = 0
            for (rec in iList) {
                // If record's date range overlaps [startLocal, endLocal], count it
                // For simplicity, each izin record counts as 1 regardless of duration overlapping,
                // unless it spans multiple days fully inside range? Spec says per record, so count 1 per overlapping record.
                // To be thorough, we count days overlapping if record spans.
                val overlapDays = countOverlapDays(rec.startDate, rec.endDate, startLocal, endLocal)
                if (overlapDays > 0) {
                    when (rec.tipe) {
                        com.gynda.fridaystm.util.IzinTypeValue.IZIN -> totalIzin += 1 // or overlapDays if per-day
                        com.gynda.fridaystm.util.IzinTypeValue.SAKIT -> totalSakit += 1
                        else -> totalIzin += 1
                    }
                }
            }

            val lList = larkamByUser[user.uid].orEmpty()
            var totalMeters = 0.0
            for (rec in lList) {
                val meters = when {
                    rec.distanceMeters != 0.0 -> rec.distanceMeters
                    rec.distanceKm != 0f -> rec.distanceKm * 1000.0
                    else -> 0.0
                }
                // also support alternative field names via fallback map? For now handle both.
                // Additional fallback: check if larkam doc had distanceInMeters field (handled via distanceMeters)
                totalMeters += meters
            }

            // Alfa = expected days - (hadir+terlambat+izin+sakit), floored at 0
            // If distinctAttendanceDates == 0, treat expected as hadir+terlambat+izin+sakit => alfa 0
            val attendedOrExcused = hadir + terlambat + totalIzin + totalSakit
            val alfa = if (distinctAttendanceDates == 0) 0 else (distinctAttendanceDates - attendedOrExcused).coerceAtLeast(0)

            StudentSummaryReport(
                studentUid = user.uid,
                studentName = user.nama,
                studentNis = user.nis,
                kelas = user.kelas,
                totalHadir = hadir,
                totalTerlambat = terlambat,
                totalIzin = totalIzin,
                totalSakit = totalSakit,
                totalAlfa = alfa,
                totalLarkamDistanceMeters = totalMeters,
                totalLarkamSessions = lList.size,
            )
        }.sortedBy { it.studentName }
    }

    private fun extractDate(timestamp: String): LocalDate? = try {
        val datePart = timestamp.substringBefore("T").substringBefore(" ")
        LocalDate.parse(datePart)
    } catch (_: Exception) { null }

    private fun isLate(timestamp: String): Boolean {
        return try {
            val timePart = if (timestamp.contains("T")) timestamp.substringAfter("T").substringBefore(".").take(5) else ""
            if (timePart.length < 5) false else timePart >= LATE_CUTOFF
        } catch (_: Exception) { false }
    }

    private fun countOverlapDays(start: String, end: String, rangeStart: LocalDate, rangeEnd: LocalDate): Int {
        return try {
            val s = LocalDate.parse(start)
            val e = LocalDate.parse(end)
            val overlapStart = if (s.isAfter(rangeStart)) s else rangeStart
            val overlapEnd = if (e.isBefore(rangeEnd)) e else rangeEnd
            if (overlapEnd.isBefore(overlapStart)) 0 else 1 // count as 1 per overlapping record (spec per record, not per day)
        } catch (_: Exception) { 0 }
    }

    /** Helper for CSV/PDF: format meters to km string with 2 decimals. */
    fun metersToKmString(meters: Double): String =
        String.format(java.util.Locale.US, "%.2f", meters / 1000.0)
}
