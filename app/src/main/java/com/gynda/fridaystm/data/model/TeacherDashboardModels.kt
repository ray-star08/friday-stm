package com.gynda.fridaystm.data.model

import com.google.firebase.firestore.DocumentId

/**
 * Aggregated daily stats for a class — shown in 4 summary cards.
 *
 * Computed client-side from the filtered presensi/izin/larkam streams
 * + total student count for the selected class.
 */
data class TeacherStats(
    val totalHadir: Int = 0,
    val totalIzin: Int = 0,
    val totalBelum: Int = 0,
    val totalLarkamKm: Double = 0.0,
    val totalNeedsReview: Int = 0,
)

/** Presence badge shown per student in the list. */
enum class StudentPresenceStatus {
    HADIR,
    PARTIAL,
    LEGACY,
    NEEDS_REVIEW,
    IZIN,
    BELUM,
}

/**
 * One row in the teacher's student list.
 *
 * - HADIR: has a `PresensiRecord` for the selected date → show foto + jam
 * - IZIN: has an `IzinRecord` covering the date → kuning
 * - BELUM: neither → merah (alpha)
 *
 * @property presensi the matching presensi doc when HADIR, else null
 * @property izin the matching izin doc when IZIN, else null
 * @property larkamDistanceKm distance for this student that day (if any)
 */
data class StudentAttendanceItem(
    val user: User,
    val status: StudentPresenceStatus,
    val presensi: PresensiRecord? = null,
    val izin: IzinRecord? = null,
    val larkamDistanceKm: Double? = null,
    val day: AttendanceDay? = null,
)

/**
 * Larkam record for teacher aggregation — subset of `larkam_records` fields.
 * Firestore `larkam_records/{autoId}` written by PresensiCameraViewModel
 * when a Larkam selfie is finished (contains distanceKm etc.).
 */
data class LarkamRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val distanceKm: Float = 0f,
    val distanceMeters: Double = 0.0,
    val studentClass: String = "",
    val timestamp: String = "",
    val imageUrl: String = "",
)

/** Pure helper to compute stats from raw lists — unit-testable. */
fun calculateTeacherStats(
    totalStudents: Int,
    hadirUserIds: Set<String>,
    izinUserIds: Set<String>,
    larkamRecords: List<LarkamRecord>,
): TeacherStats {
    val hadir = hadirUserIds.size
    // avoid double-count: a hadir student is not counted as izin
    val izinOnly = (izinUserIds - hadirUserIds).size
    val belum = (totalStudents - hadir - izinOnly).coerceAtLeast(0)
    val totalLarkamKm = larkamRecords.sumOf {
        when {
            it.distanceKm != 0f -> it.distanceKm.toDouble()
            it.distanceMeters != 0.0 -> it.distanceMeters / 1000.0
            else -> 0.0
        }
    }
    return TeacherStats(
        totalHadir = hadir,
        totalIzin = izinOnly,
        totalBelum = belum,
        totalLarkamKm = totalLarkamKm,
    )
}

/** Shared lifecycle aggregation entry points. */
fun buildDayAttendanceList(users: List<User>, days: List<AttendanceDay>, izin: List<IzinRecord>, larkam: List<LarkamRecord>): List<StudentAttendanceItem> {
    val daysByUser = days.associateBy { it.userId }
    val izinByUser = izin.filter { it.status == com.gynda.fridaystm.util.IzinStatus.APPROVED }
        .sortedWith(compareBy<IzinRecord> { it.tipe == com.gynda.fridaystm.util.IzinTypeValue.SAKIT }.thenBy { it.id })
        .associateBy { it.userId }
    val distances = larkam.groupBy { it.userId }.mapValues { (_, records) -> records.sumOf {
        if (it.distanceKm != 0f) it.distanceKm.toDouble() else it.distanceMeters / 1000.0
    } }
    return users.distinctBy { it.uid }.sortedBy { it.nama }.map { user ->
        val day = daysByUser[user.uid]
        val permit = izinByUser[user.uid].takeIf { day == null }
        val status = when (day?.status) {
            AttendanceDayStatus.COMPLETE -> StudentPresenceStatus.HADIR
            AttendanceDayStatus.PARTIAL -> StudentPresenceStatus.PARTIAL
            AttendanceDayStatus.LEGACY -> StudentPresenceStatus.LEGACY
            AttendanceDayStatus.NEEDS_REVIEW -> StudentPresenceStatus.NEEDS_REVIEW
            null -> if (permit != null) StudentPresenceStatus.IZIN else StudentPresenceStatus.BELUM
        }
        StudentAttendanceItem(user, status, day?.presensi, permit, distances[user.uid], day)
    }
}

fun calculateDayTeacherStats(items: List<StudentAttendanceItem>): TeacherStats = TeacherStats(
    totalHadir = items.count { it.day?.countsAsPresent == true },
    totalIzin = items.count { it.status == StudentPresenceStatus.IZIN },
    totalBelum = items.count { it.status == StudentPresenceStatus.BELUM },
    totalNeedsReview = items.count { it.status == StudentPresenceStatus.NEEDS_REVIEW },
    totalLarkamKm = items.sumOf { it.larkamDistanceKm ?: 0.0 },
)

/** Build student list with status by joining users vs presensi/izin maps. */
fun buildStudentAttendanceList(
    users: List<User>,
    presensiByUserId: Map<String, PresensiRecord>,
    izinByUserId: Map<String, IzinRecord>,
    larkamByUserId: Map<String, LarkamRecord>,
): List<StudentAttendanceItem> = users.map { user ->
    val presensi = presensiByUserId[user.uid]
    val izin = izinByUserId[user.uid]
    val larkam = larkamByUserId[user.uid]
    val status = when {
        presensi != null -> StudentPresenceStatus.HADIR
        izin != null -> StudentPresenceStatus.IZIN
        else -> StudentPresenceStatus.BELUM
    }
    val larkamKm = larkam?.let {
        if (it.distanceKm != 0f) it.distanceKm.toDouble() else it.distanceMeters / 1000.0
    }
    StudentAttendanceItem(
        user = user,
        status = status,
        presensi = presensi,
        izin = izin,
        larkamDistanceKm = larkamKm,
    )
}
