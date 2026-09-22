package com.gynda.fridaystm.data.repository

import com.gynda.fridaystm.data.model.IzinRecord
import com.gynda.fridaystm.data.model.LarkamRecord
import com.gynda.fridaystm.data.model.PresensiRecord
import com.gynda.fridaystm.data.model.ReportAggregator
import com.gynda.fridaystm.data.model.User
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Tests for ReportAggregator pure logic — verifies that presensi counts
 * (hadir vs terlambat via 07:00 cutoff) and larkam distance summation
 * (meters + km*1000) are correctly aggregated per student.
 */
class ReportRepositoryTest {

    private fun user(uid: String, kelas: String) = User(uid = uid, nis = "NIS$uid", nama = "Siswa $uid", kelas = kelas)

    private fun presensi(userId: String, timestampIso: String, kelas: String) = PresensiRecord(
        userId = userId, timestamp = timestampIso, studentClass = kelas,
    )

    private fun izin(userId: String, tipe: String, kelas: String) = IzinRecord(
        userId = userId, kelas = kelas, tipe = tipe, status = "APPROVED",
        startDate = "2026-09-01", endDate = "2026-09-01",
    )

    private fun larkam(userId: String, meters: Double = 0.0, km: Float = 0f) = LarkamRecord(
        userId = userId, distanceMeters = meters, distanceKm = km, timestamp = "2026-09-02T07:00:00",
    )

    @Test
    fun aggregateData_correctlyCalculatesPresensiAndLarkamDistance() {
        val kelas = "XI RPL A"
        val users = listOf(user("u1", kelas), user("u2", kelas), user("u3", kelas))

        // u1: 2 hadir early, 1 terlambat (07:15), total 3 presensi
        // u2: 0 presensi, 1 izin, 1 sakit
        // u3: 1 hadir
        val presensi = listOf(
            presensi("u1", "2026-09-01T06:45:00", kelas), // hadir
            presensi("u1", "2026-09-02T06:50:00", kelas), // hadir
            presensi("u1", "2026-09-03T07:15:00", kelas), // terlambat
            presensi("u3", "2026-09-01T06:30:00", kelas), // hadir
        )
        val izin = listOf(
            izin("u2", "IZIN", kelas), // IZIN
            izin("u2", "SAKIT", kelas).copy(startDate = "2026-09-02", endDate = "2026-09-02"), // distinct session
        )
        // Larkam: u1 has 1500m + 2500m = 4000m (2 sessions), u2 has 1000m, u3 none
        // Test both distanceMeters and distanceKm paths: use meters for u1 first, km for second
        val larkam = listOf(
            larkam("u1", meters = 1500.0),
            larkam("u1", km = 2.5f), // 2500m
            larkam("u2", meters = 1000.0),
        )

        val startDate = LocalDate.parse("2026-09-01").atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endDate = LocalDate.parse("2026-09-07").atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val result = ReportAggregator.aggregate(users, presensi, izin, larkam, startDate, endDate)

        // Find per student
        val r1 = result.first { it.studentUid == "u1" }
        val r2 = result.first { it.studentUid == "u2" }
        val r3 = result.first { it.studentUid == "u3" }

        // u1: hadir 2, terlambat 1, izin 0, sakit 0, larkam 4000
        assertEquals(2, r1.totalHadir)
        assertEquals(1, r1.totalTerlambat)
        assertEquals(0, r1.totalIzin)
        assertEquals(0, r1.totalSakit)
        assertEquals(4000.0, r1.totalLarkamDistanceMeters, 0.01)
        assertEquals(2, r1.totalLarkamSessions)
        // Conversion check: metersToKmString should be 4.00 km
        assertEquals("4.00", ReportAggregator.metersToKmString(r1.totalLarkamDistanceMeters))

        // u2: hadir 0, terlambat 0, izin 1, sakit 1
        assertEquals(0, r2.totalHadir)
        assertEquals(0, r2.totalTerlambat)
        assertEquals(1, r2.totalIzin)
        assertEquals(1, r2.totalSakit)
        assertEquals(1000.0, r2.totalLarkamDistanceMeters, 0.01)
        assertEquals(1, r2.totalLarkamSessions)
        assertEquals("1.00", ReportAggregator.metersToKmString(r2.totalLarkamDistanceMeters))

        // u3: hadir 1
        assertEquals(1, r3.totalHadir)
        assertEquals(0, r3.totalTerlambat)
        assertEquals(0.0, r3.totalLarkamDistanceMeters, 0.01)
        assertEquals(0, r3.totalLarkamSessions)
    }

    @Test
    fun duplicateDayUsesEarliestValidCaptureRegardlessOfInputOrder() {
        val start = LocalDate.parse("2026-09-01").atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val records = listOf(
            presensi("u1", "2026-09-01T06:45:00", "X"),
            presensi("u1", "2026-09-01T07:15:00", "X"),
        )
        for (input in listOf(records, records.reversed())) {
            val result = ReportAggregator.aggregate(listOf(user("u1", "X")), input, emptyList(), emptyList(), start, start).single()
            assertEquals(1, result.totalHadir)
            assertEquals(0, result.totalTerlambat)
        }
    }

    @Test
    fun onlyApprovedPermitsCount() {
        val start = LocalDate.parse("2026-09-01").atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val permits = listOf(
            izin("u1", "IZIN", "X"),
            izin("u1", "IZIN", "X").copy(status = "PENDING"),
            izin("u1", "SAKIT", "X").copy(status = "REJECTED"),
        )
        val result = ReportAggregator.aggregate(listOf(user("u1", "X")), emptyList(), permits, emptyList(), start, start).single()
        assertEquals(1, result.totalIzin)
        assertEquals(0, result.totalSakit)
    }

    @Test
    fun aggregate_larkamDistanceKmConversion_isAccurate() {
        val user = user("u1", "X")
        val larkam = listOf(
            LarkamRecord(userId = "u1", distanceKm = 1.5f), // 1500m
            LarkamRecord(userId = "u1", distanceMeters = 500.0),
        )
        val start = LocalDate.parse("2026-09-01").atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = LocalDate.parse("2026-09-02").atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val result = ReportAggregator.aggregate(listOf(user), emptyList(), emptyList(), larkam, start, end)
        val r = result.first()
        assertEquals(2000.0, r.totalLarkamDistanceMeters, 0.01)
        assertEquals("2.00", ReportAggregator.metersToKmString(r.totalLarkamDistanceMeters))
    }
}
