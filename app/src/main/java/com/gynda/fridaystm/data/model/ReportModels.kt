package com.gynda.fridaystm.data.model

import com.gynda.fridaystm.util.IzinStatus
import com.gynda.fridaystm.util.IzinTypeValue
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Recorded-day totals, not verified punctuality or academic-calendar absence.
 * totalHadir/totalTerlambat partition only valid HH:mm display times (07:00 WIB).
 * Missing/malformed display time stays recorded in totalUnknownTime and lifecycle totals.
 * totalAlfa is retained for compatibility: provisional "Belum tercatat" on observed days.
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
    val totalComplete: Int = 0,
    val totalPartial: Int = 0,
    val totalLegacy: Int = 0,
    val totalNeedsReview: Int = 0,
    val totalUnknownTime: Int = 0,
)

/** Epoch bounds interpreted as school-local dates in Asia/Jakarta, inclusive. */
data class RekapReportFilter(
    val kelas: String = "",
    val startDate: Long = 0L,
    val endDate: Long = 0L,
    val format: ExportFormat = ExportFormat.PDF,
)

enum class ExportFormat { PDF, EXCEL_CSV }

/** Pure compatibility aggregation. No calendar/holiday or historical roster policy is inferred. */
object ReportAggregator {
    private val schoolZone = ZoneId.of("Asia/Jakarta")
    private val lateCutoff = LocalTime.of(7, 0)
    private val displayTimePattern = Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]")

    /** Legacy callers use the same projection and totals as canonical consumers. Invalid evidence fails. */
    fun aggregate(
        users: List<User>,
        presensi: List<PresensiRecord>,
        izin: List<IzinRecord>,
        larkam: List<LarkamRecord>,
        startDate: Long,
        endDate: Long,
    ): List<StudentSummaryReport> = aggregateDays(
        users, AttendanceDayProjector.merge(emptyList(), presensi), izin, larkam, startDate, endDate,
    )

    /**
     * One record or approved permit per user/date; any record (including review) wins.
     * SAKIT wins conflicting approved permits. Observed record dates alone define the
     * provisional denominator; permits on other dates cannot erase missing observed sessions.
     * Larkam inputs retain the existing pre-filtered distance/session contract.
     */
    fun aggregateDays(
        users: List<User>,
        days: List<AttendanceDay>,
        izin: List<IzinRecord>,
        larkam: List<LarkamRecord>,
        startDate: Long,
        endDate: Long,
    ): List<StudentSummaryReport> {
        require(startDate <= endDate) { "startDate must be <= endDate" }
        val start = Instant.ofEpochMilli(startDate).atZone(schoolZone).toLocalDate()
        val end = Instant.ofEpochMilli(endDate).atZone(schoolZone).toLocalDate()
        val roster = users.associateBy { it.uid }
        val scopedDays = days.filter { it.userId in roster && LocalDate.parse(it.date) in start..end }.distinct()
        val observedDates = scopedDays.map { it.date }.toSet()
        val daysByUser = scopedDays.groupBy { it.userId }
        val permitsByUser = izin.filter { it.status == IzinStatus.APPROVED }.groupBy { it.userId }
        val larkamByUser = larkam.groupBy { it.userId }
        return users.map { user ->
            val recorded = daysByUser[user.uid].orEmpty()
            val recordedDates = recorded.map { it.date }.toSet()
            val present = recorded.filter { it.countsAsPresent }
            val validTimes = present.mapNotNull { day ->
                day.time.takeIf { displayTimePattern.matches(it) }?.let(LocalTime::parse)
            }
            val permits = mutableMapOf<String, String>()
            for (permit in permitsByUser[user.uid].orEmpty()) {
                val from = runCatching { LocalDate.parse(permit.startDate) }.getOrNull() ?: continue
                val until = runCatching { LocalDate.parse(permit.endDate) }.getOrNull() ?: continue
                var date = maxOf(from, start)
                val last = minOf(until, end)
                while (date <= last) {
                    val key = date.toString()
                    if (key !in recordedDates && (permit.tipe == IzinTypeValue.SAKIT || key !in permits)) {
                        permits[key] = permit.tipe
                    }
                    date = date.plusDays(1)
                }
            }
            val runs = larkamByUser[user.uid].orEmpty()
            StudentSummaryReport(
                studentUid = user.uid,
                studentName = user.nama,
                studentNis = user.nis,
                kelas = user.kelas,
                totalHadir = validTimes.count { it < lateCutoff },
                totalTerlambat = validTimes.count { it >= lateCutoff },
                totalUnknownTime = present.size - validTimes.size,
                totalIzin = permits.values.count { it != IzinTypeValue.SAKIT },
                totalSakit = permits.values.count { it == IzinTypeValue.SAKIT },
                totalAlfa = observedDates.count { it !in recordedDates && it !in permits },
                totalComplete = recorded.count { it.status == AttendanceDayStatus.COMPLETE },
                totalPartial = recorded.count { it.status == AttendanceDayStatus.PARTIAL },
                totalLegacy = recorded.count { it.status == AttendanceDayStatus.LEGACY },
                totalNeedsReview = recorded.count { it.status == AttendanceDayStatus.NEEDS_REVIEW },
                totalLarkamDistanceMeters = runs.sumOf {
                    if (it.distanceMeters != 0.0) it.distanceMeters else it.distanceKm * 1000.0
                },
                totalLarkamSessions = runs.size,
            )
        }.sortedBy { it.studentName }
    }

    /** Helper for CSV/PDF: format meters to km string with 2 decimals. */
    fun metersToKmString(meters: Double): String =
        String.format(java.util.Locale.US, "%.2f", meters / 1000.0)
}
