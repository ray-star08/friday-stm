package com.gynda.fridaystm.util.seeder

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.gynda.fridaystm.data.model.IzinRecord
import com.gynda.fridaystm.data.model.LarkamRecord
import com.gynda.fridaystm.data.model.PresensiRecord
import com.gynda.fridaystm.data.model.User
import com.gynda.fridaystm.util.FirestoreCollections
import com.gynda.fridaystm.util.IzinStatus
import com.gynda.fridaystm.util.IzinTypeValue
import com.gynda.fridaystm.util.UserRole
import java.time.LocalDate
import kotlinx.coroutines.tasks.await

/**
 * Firestore Mock Data Seeder for testing & demo purposes (Teacher Dashboard,
 * student attendance, permits, and Larkam runs) across all 6 demo classes.
 */
object FirestoreDataSeeder {

    /**
     * Safely and idempotently populates mock student profiles and attendance records
     * for all 6 classes: XI RPL A, XI RPL B, XI RPL C, XI TKJ A, XI TKJ B, X RPL A.
     */
    suspend fun seedMockDataIfEmpty(
        context: Context,
        firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    ): Result<Unit> = runCatching {
        val today = LocalDate.now().toString()

        // 1. Users Profile Batch
        try {
            val usersRef = firestore.collection(FirestoreCollections.USERS)
            val batchUsers = firestore.batch()
            val dummyStudents = listOf(
                // XI RPL A
                User(uid = "siswa_rpl_a_01", nis = "23241001", nama = "Gynda Rayhan Jati Prabowo", grade = 11, kelas = "XI RPL A", role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Gynda+Rayhan"),
                User(uid = "siswa_rpl_a_02", nis = "23241002", nama = "Ransya", grade = 11, kelas = "XI RPL A", role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Ransya"),
                User(uid = "siswa_rpl_a_03", nis = "23241003", nama = "Wirya", grade = 11, kelas = "XI RPL A", role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Wirya"),
                User(uid = "siswa_rpl_a_04", nis = "23241004", nama = "Alrizky", grade = 11, kelas = "XI RPL A", role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Alrizky"),
                // XI RPL B
                User(uid = "siswa_rpl_b_01", nis = "23242001", nama = "Budi Santoso", grade = 11, kelas = "XI RPL B", role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Budi+Santoso"),
                User(uid = "siswa_rpl_b_02", nis = "23242002", nama = "Siti Aminah", grade = 11, kelas = "XI RPL B", role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Siti+Aminah"),
                // XI RPL C
                User(uid = "siswa_rpl_c_01", nis = "23243001", nama = "Ahmad Dani", grade = 11, kelas = "XI RPL C", role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Ahmad+Dani"),
                User(uid = "siswa_rpl_c_02", nis = "23243002", nama = "Dewi Lestari", grade = 11, kelas = "XI RPL C", role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Dewi+Lestari"),
                // XI TKJ A
                User(uid = "siswa_tkj_a_01", nis = "23244001", nama = "Eko Prasetyo", grade = 11, kelas = "XI TKJ A", role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Eko+Prasetyo"),
                User(uid = "siswa_tkj_a_02", nis = "23244002", nama = "Rina Wati", grade = 11, kelas = "XI TKJ A", role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Rina+Wati"),
                // XI TKJ B
                User(uid = "siswa_tkj_b_01", nis = "23245001", nama = "Joko Widodo", grade = 11, kelas = "XI TKJ B", role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Joko+Widodo"),
                // X RPL A
                User(uid = "siswa_xrpl_01", nis = "23246001", nama = "Citra Kirana", grade = 10, kelas = "X RPL A", role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Citra+Kirana"),
            )
            dummyStudents.forEach { student ->
                batchUsers.set(usersRef.document(student.uid), student)
            }
            batchUsers.commit().await()
        } catch (_: Exception) { /* best-effort user seeding */ }

        // 2. Presensi Records Batch
        try {
            val presensiRef = firestore.collection(FirestoreCollections.PRESENSI_RECORDS)
            val batchPresensi = firestore.batch()
            val samplePresensi = listOf(
                PresensiRecord(userId = "siswa_rpl_a_01", timestamp = "${today}T07:15:00", imageUrl = "https://picsum.photos/seed/p1/300/300", studentName = "Gynda Rayhan Jati Prabowo", studentClass = "XI RPL A", lat = -6.9, lng = 107.5),
                PresensiRecord(userId = "siswa_rpl_a_02", timestamp = "${today}T07:30:00", imageUrl = "https://picsum.photos/seed/p2/300/300", studentName = "Ransya", studentClass = "XI RPL A", lat = -6.9, lng = 107.5),
                PresensiRecord(userId = "siswa_rpl_b_01", timestamp = "${today}T07:10:00", imageUrl = "https://picsum.photos/seed/p3/300/300", studentName = "Budi Santoso", studentClass = "XI RPL B", lat = -6.9, lng = 107.5),
                PresensiRecord(userId = "siswa_tkj_a_01", timestamp = "${today}T07:20:00", imageUrl = "https://picsum.photos/seed/p4/300/300", studentName = "Eko Prasetyo", studentClass = "XI TKJ A", lat = -6.9, lng = 107.5),
                PresensiRecord(userId = "siswa_xrpl_01", timestamp = "${today}T07:25:00", imageUrl = "https://picsum.photos/seed/p5/300/300", studentName = "Citra Kirana", studentClass = "X RPL A", lat = -6.9, lng = 107.5),
            )
            samplePresensi.forEach { record ->
                // Deterministic id → re-running the seeder overwrites instead of
                // duplicating (idempotent demo data).
                batchPresensi.set(presensiRef.document("seed_${today}_${record.userId}"), record)
            }
            batchPresensi.commit().await()
        } catch (_: Exception) { /* best-effort presensi seeding */ }

        // 3. Izin Records Batch
        try {
            val izinRef = firestore.collection(FirestoreCollections.IZIN_RECORDS)
            val batchIzin = firestore.batch()
            val sampleIzin = listOf(
                IzinRecord(userId = "siswa_rpl_a_03", nama = "Wirya", kelas = "XI RPL A", tipe = IzinTypeValue.SAKIT, alasan = "Demam dan flu berat", startDate = today, endDate = today, proofUrl = "https://picsum.photos/seed/i1/400/400", status = IzinStatus.PENDING),
                IzinRecord(userId = "siswa_rpl_b_02", nama = "Siti Aminah", kelas = "XI RPL B", tipe = IzinTypeValue.IZIN, alasan = "Keperluan keluarga mendadak", startDate = today, endDate = today, proofUrl = "https://picsum.photos/seed/i2/400/400", status = IzinStatus.PENDING),
                IzinRecord(userId = "siswa_tkj_a_02", nama = "Rina Wati", kelas = "XI TKJ A", tipe = IzinTypeValue.SAKIT, alasan = "Izin berobat ke dokter", startDate = today, endDate = today, proofUrl = "https://picsum.photos/seed/i3/400/400", status = IzinStatus.PENDING),
            )
            sampleIzin.forEach { izin ->
                batchIzin.set(izinRef.document("seed_${today}_${izin.userId}"), izin)
            }
            batchIzin.commit().await()
        } catch (_: Exception) { /* best-effort izin seeding */ }

        // 4. Larkam Records Batch
        try {
            val larkamRef = firestore.collection(FirestoreCollections.LARKAM_RECORDS)
            val batchLarkam = firestore.batch()
            val sampleLarkam = listOf(
                LarkamRecord(userId = "siswa_rpl_a_01", distanceKm = 2.4f, studentClass = "XI RPL A", timestamp = "${today}T08:05:00", imageUrl = "https://picsum.photos/seed/l1/300/300"),
                LarkamRecord(userId = "siswa_rpl_a_02", distanceKm = 3.1f, studentClass = "XI RPL A", timestamp = "${today}T08:10:00", imageUrl = "https://picsum.photos/seed/l2/300/300"),
                LarkamRecord(userId = "siswa_rpl_b_01", distanceKm = 1.8f, studentClass = "XI RPL B", timestamp = "${today}T08:07:00", imageUrl = "https://picsum.photos/seed/l3/300/300"),
                LarkamRecord(userId = "siswa_tkj_a_01", distanceKm = 2.9f, studentClass = "XI TKJ A", timestamp = "${today}T08:12:00", imageUrl = "https://picsum.photos/seed/l4/300/300"),
            )
            sampleLarkam.forEach { larkam ->
                batchLarkam.set(larkamRef.document("seed_${today}_${larkam.userId}"), larkam)
            }
            batchLarkam.commit().await()
        } catch (_: Exception) { /* best-effort larkam seeding */ }

        Unit
    }
}
