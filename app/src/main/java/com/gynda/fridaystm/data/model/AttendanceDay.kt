package com.gynda.fridaystm.data.model

import com.gynda.fridaystm.util.AttendanceStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Display lifecycle, not a server attestation of physical attendance. */
enum class AttendanceDayStatus { COMPLETE, PARTIAL, NEEDS_REVIEW, LEGACY }

/** Read-only projection. No phase or verification evidence is synthesized. */
data class AttendanceDay(
    val userId: String,
    val date: String,
    val status: AttendanceDayStatus,
    val attendance: AttendanceRecord? = null,
    val presensi: PresensiRecord? = null,
) {
    val id: String get() = "${userId}_$date"
    val time: String get() = attendance?.let { record ->
        record.pembiasaan?.takeIf { it.checkedIn }?.time
            ?: record.apel?.takeIf { it.checkedIn }?.time.orEmpty()
    } ?: presensi?.let { schoolCaptureTime(it.timestamp).format(DateTimeFormatter.ofPattern("HH:mm")) }.orEmpty()
    val imageUrl: String get() = listOfNotNull(
        attendance?.pembiasaan?.selfieUrl, attendance?.apel?.selfieUrl, presensi?.imageUrl,
    ).firstOrNull { it.isNotBlank() }.orEmpty()
    val countsAsPresent: Boolean get() = status != AttendanceDayStatus.NEEDS_REVIEW
}

/** Legacy naive times are school-local; offset times are normalized to school time. */
internal fun schoolCaptureTime(timestamp: String): LocalDateTime =
    runCatching { OffsetDateTime.parse(timestamp).atZoneSameInstant(ZoneId.of("Asia/Jakarta")).toLocalDateTime() }
        .recoverCatching { LocalDateTime.parse(timestamp) }
        .getOrElse { throw IllegalArgumentException("Timestamp presensi tidak valid", it) }

/** Merge both storage contracts without modifying evidence. Canonical state wins. */
object AttendanceDayProjector {
    fun merge(attendance: List<AttendanceRecord>, presensi: List<PresensiRecord>): List<AttendanceDay> {
        attendance.forEach {
            require(it.uid.isNotBlank()) { "UID attendance kosong" }
            require(runCatching { LocalDate.parse(it.date).toString() == it.date }.getOrDefault(false)) {
                "Tanggal attendance tidak valid"
            }
        }
        attendance.groupBy { it.uid to it.date }.values.forEach {
            require(it.distinct().size == 1) { "Duplikat attendance bertentangan" }
        }
        val legacy = presensi.map {
            require(it.userId.isNotBlank()) { "UID presensi kosong" }
            it to schoolCaptureTime(it.timestamp)
        }
            .sortedWith(compareBy({ it.second }, { it.first.id }))
            .distinctBy { it.first.userId to it.second.toLocalDate() }
            .associate { (record, time) ->
                val date = time.toLocalDate().toString()
                (record.userId to date) to AttendanceDay(record.userId, date, AttendanceDayStatus.LEGACY, presensi = record)
            }
        val days = legacy.toMutableMap()
        attendance.forEach { record ->
            val key = record.uid to record.date
            days[key] = AttendanceDay(record.uid, record.date, status(record), record, legacy[key]?.presensi)
        }
        return days.values.sortedWith(compareByDescending<AttendanceDay> { it.date }.thenBy { it.userId })
    }

    private fun status(record: AttendanceRecord): AttendanceDayStatus {
        val invalidStamp = record.pembiasaan?.let { !it.checkedIn || !it.valid } == true ||
            record.apel?.let { !it.checkedIn || !it.valid } == true
        return when {
            invalidStamp -> AttendanceDayStatus.NEEDS_REVIEW
            record.status == AttendanceStatus.COMPLETE && record.pembiasaan?.checkedIn == true &&
                record.checkout?.checkedOut == true -> AttendanceDayStatus.COMPLETE
            record.status == AttendanceStatus.INCOMPLETE && record.checkout == null &&
                (record.pembiasaan?.checkedIn == true || record.apel?.checkedIn == true) -> AttendanceDayStatus.PARTIAL
            else -> AttendanceDayStatus.NEEDS_REVIEW
        }
    }
}
