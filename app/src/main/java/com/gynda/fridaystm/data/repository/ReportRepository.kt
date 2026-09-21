package com.gynda.fridaystm.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.gynda.fridaystm.data.model.IzinRecord
import com.gynda.fridaystm.data.model.LarkamRecord
import com.gynda.fridaystm.data.model.PresensiRecord
import com.gynda.fridaystm.data.model.ReportAggregator
import com.gynda.fridaystm.data.model.StudentSummaryReport
import com.gynda.fridaystm.data.model.User
import com.gynda.fridaystm.util.FirestoreCollections
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.ZoneId

interface ReportRepository {
    /**
     * Fetch and aggregate summary for a class within [startDate, endDate] inclusive (epoch millis).
     * Firestore queries are by kelas + client-side date filtering (timestamp ISO).
     */
    suspend fun getRekapSummary(kelas: String, startDate: Long, endDate: Long): Result<List<StudentSummaryReport>>
}

class FirebaseReportRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : ReportRepository {

    override suspend fun getRekapSummary(kelas: String, startDate: Long, endDate: Long): Result<List<StudentSummaryReport>> = runCatching {
        require(kelas.isNotBlank()) { "kelas must not be blank" }
        require(startDate <= endDate) { "startDate must be <= endDate" }

        val startLocal = Instant.ofEpochMilli(startDate).atZone(ZoneId.systemDefault()).toLocalDate()
        val endLocal = Instant.ofEpochMilli(endDate).atZone(ZoneId.systemDefault()).toLocalDate()

        // 1) Users in class
        val usersSnap = firestore.collection(FirestoreCollections.USERS)
            .whereEqualTo("kelas", kelas)
            .get().await()
        val users = usersSnap.toObjects(User::class.java)

        // 2) Presensi for class in range - filter client-side by timestamp date
        val presensiSnap = firestore.collection(FirestoreCollections.PRESENSI_RECORDS)
            .whereEqualTo("studentClass", kelas)
            .get().await()
        val allPresensi = presensiSnap.toObjects(PresensiRecord::class.java)
        val presensiInRange = allPresensi.filter { rec ->
            val date = rec.timestamp.substringBefore("T").substringBefore(" ")
            try {
                val d = java.time.LocalDate.parse(date)
                !d.isBefore(startLocal) && !d.isAfter(endLocal)
            } catch (_: Exception) { false }
        }

        // 3) Izin for class overlapping range
        val izinSnap = firestore.collection(FirestoreCollections.IZIN_RECORDS)
            .whereEqualTo("kelas", kelas)
            .get().await()
        val allIzin = izinSnap.toObjects(IzinRecord::class.java)
        val izinInRange = allIzin.filter { rec ->
            try {
                val s = java.time.LocalDate.parse(rec.startDate)
                val e = java.time.LocalDate.parse(rec.endDate)
                !(e.isBefore(startLocal) || s.isAfter(endLocal))
            } catch (_: Exception) { false }
        }

        // 4) Larkam for users in class within range
        // Larkam docs may not have kelas field, so query without kelas filter and narrow by userId + timestamp
        val userIds = users.map { it.uid }.toSet()
        val larkamSnap = firestore.collection("larkam_records").get().await()
        val allLarkamDocs = larkamSnap.documents
        val larkamInRange = allLarkamDocs.mapNotNull { doc ->
            try {
                val userId = doc.getString("userId") ?: return@mapNotNull null
                if (userId !in userIds) return@mapNotNull null
                val ts = doc.getString("timestamp") ?: ""
                val dateStr = ts.substringBefore("T").substringBefore(" ")
                val d = java.time.LocalDate.parse(dateStr)
                if (d.isBefore(startLocal) || d.isAfter(endLocal)) return@mapNotNull null
                val km = (doc.get("distanceKm") as? Number)?.toFloat() ?: 0f
                val meters = (doc.get("distanceMeters") as? Number)?.toDouble()
                    ?: (doc.get("distanceInMeters") as? Number)?.toDouble() ?: 0.0
                LarkamRecord(
                    id = doc.id,
                    userId = userId,
                    distanceKm = km,
                    distanceMeters = meters,
                    studentClass = doc.getString("studentClass") ?: kelas,
                    timestamp = ts,
                    imageUrl = doc.getString("imageUrl") ?: "",
                )
            } catch (_: Exception) { null }
        }

        ReportAggregator.aggregate(
            users = users,
            presensi = presensiInRange,
            izin = izinInRange,
            larkam = larkamInRange,
            startDate = startDate,
            endDate = endDate,
        )
    }
}
