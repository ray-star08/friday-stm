package com.gynda.fridaystm.data.model

import org.junit.Assert.*
import org.junit.Test

class TeacherAttendanceDayTest {
    @Test
    fun `dashboard partitions roster and preserves review over approved izin`() {
        val users = (1..6).map { User(uid = "u$it", nama = "Siswa $it", kelas = "XI A") }
        val days = listOf(
            AttendanceDay("u1", "2026-09-18", AttendanceDayStatus.COMPLETE),
            AttendanceDay("u2", "2026-09-18", AttendanceDayStatus.PARTIAL),
            AttendanceDay("u3", "2026-09-18", AttendanceDayStatus.LEGACY),
            AttendanceDay("u4", "2026-09-18", AttendanceDayStatus.NEEDS_REVIEW),
            AttendanceDay("foreign", "2026-09-18", AttendanceDayStatus.COMPLETE),
        )
        val izin = listOf("u4", "u5", "foreign").map { IzinRecord(userId = it, status = "APPROVED") } +
            IzinRecord(userId = "u6", status = "PENDING")
        val items = buildDayAttendanceList(users, days, izin, emptyList())
        assertEquals(listOf("HADIR", "PARTIAL", "LEGACY", "NEEDS_REVIEW", "IZIN", "BELUM"), items.map { it.status.name })
        assertEquals(days[1], items[1].day)
        val stats = calculateDayTeacherStats(items)
        assertEquals(3, stats.totalHadir)
        assertEquals(1, stats.totalNeedsReview)
        assertEquals(1, stats.totalIzin)
        assertEquals(1, stats.totalBelum)
        assertEquals(users.size, stats.totalHadir + stats.totalNeedsReview + stats.totalIzin + stats.totalBelum)
    }
}
