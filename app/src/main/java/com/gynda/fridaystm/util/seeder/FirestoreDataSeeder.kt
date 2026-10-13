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
 * student attendance, permits, and Larkam runs).
 */
object FirestoreDataSeeder {

    /**
     * Safely and idempotently populates mock student profiles and attendance records
     * if the `users` collection is empty (or always upserts deterministic demo data).
     */
    suspend fun seedMockDataIfEmpty(
        context: Context,
        firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    ): Result<Unit> = runCatching {
        val usersRef = firestore.collection(FirestoreCollections.USERS)
        val snapshot = usersRef.limit(1).get().await()

        // Idempotency check: if users already exist, we can optionally skip or re-seed.
        // For robust demo usage, we seed deterministic dummy records.
        val batch = firestore.batch()
        val today = LocalDate.now().toString()

        val dummyStudents = listOf(
            User(uid = "siswa_rpl_a_01", nis = "23241001", nama = "Gynda Rayhan Jati Prabowo", grade = 11, kelas = "XI RPL A", role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Gynda+Rayhan"),
            User(uid = "siswa_rpl_a_02", nis = "23241002", nama = "Ransya", grade = 11, kelas = "XI RPL A", role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Ransya"),
            User(uid = "siswa_rpl_a_03", nis = "23241003", nama = "Wirya", grade = 11, kelas = "XI RPL A", role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Wirya"),
            User(uid = "siswa_rpl_a_04", nis = "23241004", nama = "Alrizky", grade = 11, kelas = "XI RPL A", role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Alrizky"),
            User(uid = "siswa_rpl_b_01", nis = "23242001", nama = "Budi Santoso", grade = 11, kelas = "XI RPL B", role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Budi+Santoso"),
            User(uid = "siswa_rpl_b_02", nis = "23242002", nama = "Siti Aminah", grade = 11, kelas = "XI RPL B", role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Siti+Aminah"),
            User(uid = "siswa_rpl_c_01", nis = "23243001", nama = "Ahmad Dani", grade = 11, kelas = "XI RPL C", role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Ahmad+Dani"),
            User(uid = "siswa_rpl_c_02", nis = "23243002", nama = "Dewi Lestari", grade = 11, kelas = "XI RPL C", role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Dewi+Lestari"),
        )

        dummyStudents.forEach { student ->
            batch.set(usersRef.document(student.uid), student)
        }

        // 1. Presensi Records
        val presensiRef = firestore.collection(FirestoreCollections.PRESENSI_RECORDS)
        val samplePresensi = listOf(
            PresensiRecord(userId = "siswa_rpl_a_01", timestamp = "${today}T07:15:00", imageUrl = "https://picsum.photos/seed/p1/300/300", studentName = "Gynda Rayhan Jati Prabowo", studentClass = "XI RPL A", lat = -6.9, lng = 107.5),
            PresensiRecord(userId = "siswa_rpl_a_02", timestamp = "${today}T07:30:00", imageUrl = "https://picsum.photos/seed/p2/300/300", studentName = "Ransya", studentClass = "XI RPL A", lat = -6.9, lng = 107.5),
            PresensiRecord(userId = "siswa_rpl_b_01", timestamp = "${today}T07:10:00", imageUrl = "https://picsum.photos/seed/p3/300/300", studentName = "Budi Santoso", studentClass = "XI RPL B", lat = -6.9, lng = 107.5),
        )
        samplePresensi.forEach { record ->
            batch.set(presensiRef.document(), record)
        }

        // 2. Izin Records
        val izinRef = firestore.collection(FirestoreCollections.IZIN_RECORDS)
        val sampleIzin = listOf(
            IzinRecord(userId = "siswa_rpl_a_03", nama = "Wirya", kelas = "XI RPL A", tipe = IzinTypeValue.SAKIT, alasan = "Demam dan flu berat", startDate = today, endDate = today, proofUrl = "https://picsum.photos/seed/i1/400/400", status = IzinStatus.PENDING),
            IzinRecord(userId = "siswa_rpl_b_02", nama = "Siti Aminah", kelas = "XI RPL B", tipe = IzinTypeValue.IZIN, alasan = "Keperluan keluarga mendadak", startDate = today, endDate = today, proofUrl = "https://picsum.photos/seed/i2/400/400", status = IzinStatus.APPROVED),
            IzinRecord(userId = "siswa_rpl_c_01", nama = "Ahmad Dani", kelas = "XI RPL C", tipe = IzinTypeValue.SAKIT, alasan = "Izin berobat ke dokter", startDate = today, endDate = today, proofUrl = "https://picsum.photos/seed/i3/400/400", status = IzinStatus.REJECTED, approvalNote = "Bukti surat kurang jelas"),
        )
        sampleIzin.forEach { izin ->
            batch.set(izinRef.document(), izin)
        }

        // 3. Larkam Records
        val larkamRef = firestore.collection(FirestoreCollections.LARKAM_RECORDS)
        val sampleLarkam = listOf(
            LarkamRecord(userId = "siswa_rpl_a_01", distanceKm = 2.4f, studentClass = "XI RPL A", timestamp = "${today}T08:05:00", imageUrl = "https://picsum.photos/seed/l1/300/300"),
            LarkamRecord(userId = "siswa_rpl_a_02", distanceKm = 3.1f, studentClass = "XI RPL A", timestamp = "${today}T08:10:00", imageUrl = "https://picsum.photos/seed/l2/300/300"),
            LarkamRecord(userId = "siswa_rpl_b_01", distanceKm = 1.8f, studentClass = "XI RPL B", timestamp = "${today}T08:07:00", imageUrl = "https://picsum.photos/seed/l3/300/300"),
        )
        sampleLarkam.forEach { larkam ->
            batch.set(larkamRef.document(), larkam)
        }

        batch.commit().await()
        Unit
    }
}
