package com.gynda.fridaystm.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.gynda.fridaystm.data.model.ProfileStats
import com.gynda.fridaystm.util.FirestoreCollections
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/** Recorded days use the shared read model; permit count remains applications, not approvals. */
interface ProfileStatsRepository {
    suspend fun getStats(userId: String): Result<ProfileStats>
}

internal interface ProfileAuxiliarySource {
    suspend fun distanceKm(uid: String): Double
    suspend fun permitCount(uid: String): Int
}
internal class CompatibleProfileStatsRepository(
    private val reader: AttendanceReadRepository,
    private val auxiliary: ProfileAuxiliarySource,
) : ProfileStatsRepository {
    override suspend fun getStats(userId: String): Result<ProfileStats> = runCatching {
        require(userId.isNotBlank()) { "UID kosong" }
        val days = reader.getUser(userId).getOrThrow()
        ProfileStats(
            presensiCount = days.filter { it.userId == userId }.distinctBy { it.id }.count { it.countsAsPresent },
            larkamDistanceKm = auxiliary.distanceKm(userId),
            izinCount = auxiliary.permitCount(userId),
        )
    }.onFailure { if (it is CancellationException) throw it }
}

/** All required data must load; failed queries are never replaced with zeros. */
class FirebaseProfileStatsRepository(
    firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    attendanceReader: AttendanceReadRepository = FirebaseAttendanceReadRepository(firestore),
) : ProfileStatsRepository by CompatibleProfileStatsRepository(attendanceReader, FirebaseProfileAuxiliarySource(firestore)) {
    companion object { const val LARKAM_RECORDS = "larkam_records" }
}

internal class FirebaseProfileAuxiliarySource(private val firestore: FirebaseFirestore) : ProfileAuxiliarySource {
    override suspend fun distanceKm(uid: String): Double = firestore.collection(FirebaseProfileStatsRepository.LARKAM_RECORDS)
        .whereEqualTo(USER_ID, uid).get(Source.SERVER).await().documents.sumOf {
            val km = (it.get(DISTANCE_KM) as? Number)?.toDouble() ?: 0.0
            if (km != 0.0) km else ((it.get(DISTANCE_METERS) as? Number)?.toDouble() ?: 0.0) / 1000.0
        }
    override suspend fun permitCount(uid: String): Int = firestore.collection(FirestoreCollections.IZIN_RECORDS)
        .whereEqualTo(USER_ID, uid).get(Source.SERVER).await().size()

    private companion object {
        const val USER_ID = "userId"
        const val DISTANCE_KM = "distanceKm"
        const val DISTANCE_METERS = "distanceMeters"
    }
}
