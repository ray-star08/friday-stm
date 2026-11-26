package com.gynda.fridaystm.util.seeder

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.gynda.fridaystm.data.model.PresensiRecord
import com.gynda.fridaystm.data.model.User
import com.gynda.fridaystm.util.FirestoreCollections
import com.gynda.fridaystm.util.UserRole
import java.time.LocalDate
import kotlinx.coroutines.tasks.await

/** Weekdays (Mon–Fri) from [start], [count] of them — the demo attendance calendar. */
internal fun schoolDays(start: LocalDate, count: Int): List<LocalDate> =
    generateSequence(start) { it.plusDays(1) }
        .filter { it.dayOfWeek.value <= 5 }
        .take(count)
        .toList()

/**
 * Firestore mock-data seeder for testing & demo (Teacher Dashboard + student
 * attendance). Creates:
 *  - the 3 demo login profiles (guru / siswa / admin) + 29 fill-in students,
 *    making XI RPL A exactly 34 students (rpl_a_01..04 already exist),
 *  - 30 school days of attendance history (from 3 Aug 2026) for the class,
 *  - today's attendance for everyone except the 4 students that already have
 *    a record today.
 *
 * Writes are create-only: rules allow staff to create documents but only
 * admins to update, so pre-existing docs are left untouched and a denied
 * create is simply skipped. Re-running never duplicates data.
 *
 * Demo logins (real Firebase Auth accounts):
 *  - Teacher: `guru@fridaystm.app` / `guru123`
 *  - Student: `siswa@fridaystm.app` / `siswa123`
 *  - Admin:   `admin@fridaystm.app` / `admin123`
 */
object FirestoreDataSeeder {

    private const val TAG = "FirestoreDataSeeder"

    // Real Firebase Auth uids of the demo login accounts.
    private const val DEMO_TEACHER_UID = "oJKkhTMr3KOuXTI7ihlgR1rQ30i2"
    private const val DEMO_STUDENT_UID = "5opVER5CsvT4XHlAtMCP94ztKWu2"
    private const val DEMO_ADMIN_UID = "yJdFJGVuxneUGBugZ4WXglLdaKq1"

    private const val DEMO_CLASS = "XI RPL A"
    private val HISTORY_START: LocalDate = LocalDate.of(2026, 8, 3)
    private const val HISTORY_DAYS = 30

    /** The 4 original students — profiles & today's records already exist. */
    private val existingClassStudents = listOf(
        User(uid = "siswa_rpl_a_01", nis = "23241001", nama = "Gynda Rayhan Jati Prabowo", grade = 11, kelas = DEMO_CLASS, role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Gynda+Rayhan"),
        User(uid = "siswa_rpl_a_02", nis = "23241002", nama = "Ransya", grade = 11, kelas = DEMO_CLASS, role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Ransya"),
        User(uid = "siswa_rpl_a_03", nis = "23241003", nama = "Wirya", grade = 11, kelas = DEMO_CLASS, role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Wirya"),
        User(uid = "siswa_rpl_a_04", nis = "23241004", nama = "Alrizky", grade = 11, kelas = DEMO_CLASS, role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Alrizky"),
    )

    /** Profiles the seeder creates: the 3 demo logins + 29 new students. */
    private val newProfiles = listOf(
        User(uid = DEMO_TEACHER_UID, nama = "Guru Demo", kelas = "-", role = UserRole.INSTRUCTOR, photoUrl = "https://ui-avatars.com/api/?name=Guru+Demo"),
        User(uid = DEMO_ADMIN_UID, nama = "Admin Demo", kelas = "-", role = UserRole.ADMIN, photoUrl = "https://ui-avatars.com/api/?name=Admin+Demo"),
        User(uid = DEMO_STUDENT_UID, nis = "23241999", nama = "Siswa Demo", grade = 11, kelas = DEMO_CLASS, role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Siswa+Demo"),
        User(uid = "siswa_rpl_a_05", nis = "23241005", nama = "Ahmad Fauzi", grade = 11, kelas = DEMO_CLASS, role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Ahmad+Fauzi"),
        User(uid = "siswa_rpl_a_06", nis = "23241006", nama = "Bunga Citra Melati", grade = 11, kelas = DEMO_CLASS, role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Bunga+Citra+Melati"),
        User(uid = "siswa_rpl_a_07", nis = "23241007", nama = "Cahyo Nugroho", grade = 11, kelas = DEMO_CLASS, role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Cahyo+Nugroho"),
        User(uid = "siswa_rpl_a_08", nis = "23241008", nama = "Dinda Ayu Safitri", grade = 11, kelas = DEMO_CLASS, role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Dinda+Ayu+Safitri"),
        User(uid = "siswa_rpl_a_09", nis = "23241009", nama = "Eka Saputra", grade = 11, kelas = DEMO_CLASS, role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Eka+Saputra"),
        User(uid = "siswa_rpl_a_10", nis = "23241010", nama = "Fajar Ramadhan", grade = 11, kelas = DEMO_CLASS, role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Fajar+Ramadhan"),
        User(uid = "siswa_rpl_a_11", nis = "23241011", nama = "Gita Permata Sari", grade = 11, kelas = DEMO_CLASS, role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Gita+Permata+Sari"),
        User(uid = "siswa_rpl_a_12", nis = "23241012", nama = "Hadi Susanto", grade = 11, kelas = DEMO_CLASS, role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Hadi+Susanto"),
        User(uid = "siswa_rpl_a_13", nis = "23241013", nama = "Indah Permatasari", grade = 11, kelas = DEMO_CLASS, role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Indah+Permatasari"),
        User(uid = "siswa_rpl_a_14", nis = "23241014", nama = "Joko Santoso", grade = 11, kelas = DEMO_CLASS, role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Joko+Santoso"),
        User(uid = "siswa_rpl_a_15", nis = "23241015", nama = "Kartika Sari Dewi", grade = 11, kelas = DEMO_CLASS, role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Kartika+Sari+Dewi"),
        User(uid = "siswa_rpl_a_16", nis = "23241016", nama = "Lukman Hakim", grade = 11, kelas = DEMO_CLASS, role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Lukman+Hakim"),
        User(uid = "siswa_rpl_a_17", nis = "23241017", nama = "Maya Anggraini", grade = 11, kelas = DEMO_CLASS, role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Maya+Anggraini"),
        User(uid = "siswa_rpl_a_18", nis = "23241018", nama = "Nanda Pratama", grade = 11, kelas = DEMO_CLASS, role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Nanda+Pratama"),
        User(uid = "siswa_rpl_a_19", nis = "23241019", nama = "Oscar Mahendra", grade = 11, kelas = DEMO_CLASS, role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Oscar+Mahendra"),
        User(uid = "siswa_rpl_a_20", nis = "23241020", nama = "Putri Ayu Lestari", grade = 11, kelas = DEMO_CLASS, role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Putri+Ayu+Lestari"),
        User(uid = "siswa_rpl_a_21", nis = "23241021", nama = "Qori Ramadhani", grade = 11, kelas = DEMO_CLASS, role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Qori+Ramadhani"),
        User(uid = "siswa_rpl_a_22", nis = "23241022", nama = "Rizky Kurniawan", grade = 11, kelas = DEMO_CLASS, role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Rizky+Kurniawan"),
        User(uid = "siswa_rpl_a_23", nis = "23241023", nama = "Salsabila Zahra", grade = 11, kelas = DEMO_CLASS, role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Salsabila+Zahra"),
        User(uid = "siswa_rpl_a_24", nis = "23241024", nama = "Taufik Hidayat", grade = 11, kelas = DEMO_CLASS, role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Taufik+Hidayat"),
        User(uid = "siswa_rpl_a_25", nis = "23241025", nama = "Umi Kalsum", grade = 11, kelas = DEMO_CLASS, role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Umi+Kalsum"),
        User(uid = "siswa_rpl_a_26", nis = "23241026", nama = "Vina Anggraini", grade = 11, kelas = DEMO_CLASS, role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Vina+Anggraini"),
        User(uid = "siswa_rpl_a_27", nis = "23241027", nama = "Wawan Setiawan", grade = 11, kelas = DEMO_CLASS, role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Wawan+Setiawan"),
        User(uid = "siswa_rpl_a_28", nis = "23241028", nama = "Yusuf Maulana", grade = 11, kelas = DEMO_CLASS, role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Yusuf+Maulana"),
        User(uid = "siswa_rpl_a_29", nis = "23241029", nama = "Zahra Amelia", grade = 11, kelas = DEMO_CLASS, role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Zahra+Amelia"),
        User(uid = "siswa_rpl_a_30", nis = "23241030", nama = "Andi Saputra", grade = 11, kelas = DEMO_CLASS, role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Andi+Saputra"),
        User(uid = "siswa_rpl_a_31", nis = "23241031", nama = "Bella Anjani", grade = 11, kelas = DEMO_CLASS, role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Bella+Anjani"),
        User(uid = "siswa_rpl_a_32", nis = "23241032", nama = "Candra Wijaya", grade = 11, kelas = DEMO_CLASS, role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Candra+Wijaya"),
        User(uid = "siswa_rpl_a_33", nis = "23241033", nama = "Dwi Astuti", grade = 11, kelas = DEMO_CLASS, role = UserRole.STUDENT, photoUrl = "https://ui-avatars.com/api/?name=Dwi+Astuti"),
    )

    /** XI RPL A roster for attendance generation — exactly 34 students. */
    private val classStudents = existingClassStudents + newProfiles.filter { it.kelas == DEMO_CLASS }

    /** Students that already have a record for today (skipped, create-only). */
    private val existingTodayUids = existingClassStudents.map { it.uid }.toSet()

    /**
     * Populates mock profiles and XI RPL A attendance history. Best-effort:
     * denied writes (pre-existing docs) are skipped, so this is safe to
     * re-run at any time without overwriting existing data.
     */
    suspend fun seedMockDataIfEmpty(
        context: Context,
        firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    ): Result<Unit> = runCatching {
        val today = LocalDate.now().toString()
        val usersRef = firestore.collection(FirestoreCollections.USERS)
        val presensiRef = firestore.collection(FirestoreCollections.PRESENSI_RECORDS)

        // Debug breadcrumb: existing class roster (uids) for verification.
        runCatching {
            firestore.collection(FirestoreCollections.USERS)
                .whereEqualTo("kelas", DEMO_CLASS).get().await()
                .documents.forEach { Log.d(TAG, "roster ${it.id} ${it.getString("nama")}") }
        }

        // 1) Profiles — new docs only (existing ones are admin-update-only).
        writeCreates(firestore, newProfiles.map { usersRef.document(it.uid) to it })

        // 2) Attendance history: 30 school days (Aug–Sep 2026), ~10% absence.
        val history = buildList {
            schoolDays(HISTORY_START, HISTORY_DAYS).forEachIndexed { d, date ->
                classStudents.forEachIndexed { s, student ->
                    if (isAbsent(d, s)) return@forEachIndexed
                    add(presensiWrite(presensiRef, date.toString(), d, s, student))
                }
            }
        }
        writeCreates(firestore, history)

        // 3) Today: everyone except the students that already checked in.
        val todayIndex = HISTORY_DAYS
        val todayWrites = buildList {
            classStudents.forEachIndexed { s, student ->
                if (student.uid in existingTodayUids) return@forEachIndexed
                if (isAbsent(todayIndex, s)) return@forEachIndexed
                add(presensiWrite(presensiRef, today, todayIndex, s, student))
            }
        }
        writeCreates(firestore, todayWrites)

        Unit
    }

    /** ~10% deterministic absence so the rekap screens look real. */
    private fun isAbsent(dayIndex: Int, studentIndex: Int) =
        (dayIndex * 7 + studentIndex * 13) % 10 == 0

    private fun presensiWrite(
        ref: CollectionReference,
        date: String,
        dayIndex: Int,
        studentIndex: Int,
        student: User,
    ): Pair<DocumentReference, Any> =
        ref.document("seed_${date}_${student.uid}") to PresensiRecord(
            userId = student.uid,
            timestamp = "${date}T07:${"%02d".format(5 + ((dayIndex * 3 + studentIndex * 5) % 40))}:00",
            imageUrl = "https://picsum.photos/seed/p${(studentIndex % 5) + 1}/300/300",
            studentName = student.nama,
            studentClass = student.kelas,
            lat = -6.9,
            lng = 107.5,
        )

    /**
     * Commits creates in 400-write batches. A batch is atomic, so one denied
     * write (pre-existing doc → admin-only update) aborts the whole chunk;
     * the chunk is then retried per-doc, skipping whatever is denied.
     */
    private suspend fun writeCreates(
        firestore: FirebaseFirestore,
        writes: List<Pair<DocumentReference, Any>>,
    ) {
        writes.chunked(400).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { (ref, data) -> batch.set(ref, data) }
            try {
                batch.commit().await()
            } catch (_: Exception) {
                chunk.forEach { (ref, data) -> runCatching { ref.set(data).await() } }
            }
        }
    }
}
