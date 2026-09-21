package com.gynda.fridaystm.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.gynda.fridaystm.data.model.ProfileStats
import com.gynda.fridaystm.util.FirestoreCollections
import kotlinx.coroutines.tasks.await

/**
 * Agregat statistik siswa untuk layar Profil, dihitung client-side dari
 * query Firestore milik user yang sedang masuk:
 *
 * - [ProfileStats.presensiCount]: jumlah dokumen `presensi_records`.
 * - [ProfileStats.larkamDistanceKm]: sum `distanceKm` dari `larkam_records`.
 * - [ProfileStats.izinCount]: jumlah dokumen `izin_records`.
 *
 * Tiap sumber dibaca independen dan gagal-terbuka ke 0 — satu koleksi yang
 * belum ada / tertolak rules tidak meruntuhkan seluruh ringkasan. Interface
 * (SKILL.md §9) agar ViewModel di-test dengan fake; query berupa `suspend` +
 * `.await()` → [Result] (SKILL.md §5).
 */
interface ProfileStatsRepository {
    /** Menghitung [ProfileStats] milik [userId]. */
    suspend fun getStats(userId: String): Result<ProfileStats>
}

/** Firestore-backed [ProfileStatsRepository]. */
class FirebaseProfileStatsRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : ProfileStatsRepository {

    override suspend fun getStats(userId: String): Result<ProfileStats> = runCatching {
        ProfileStats(
            presensiCount = countWhere("userId", userId, FirestoreCollections.PRESENSI_RECORDS),
            larkamDistanceKm = sumDistanceKm(userId),
            izinCount = countWhere("userId", userId, FirestoreCollections.IZIN_RECORDS),
        )
    }

    /** Jumlah dokumen di [collection] dengan `field == userId`; gagal → 0. */
    private suspend fun countWhere(field: String, userId: String, collection: String): Int =
        try {
            firestore.collection(collection)
                .whereEqualTo(field, userId)
                .get()
                .await()
                .size()
        } catch (_: Exception) {
            0
        }

    /**
     * Sum `distanceKm` dari `larkam_records` milik user; gagal → 0.0.
     * Nilai dibaca toleran ([Number], bukan `getDouble` langsung) karena
     * penulisnya menyimpan `Float`.
     */
    private suspend fun sumDistanceKm(userId: String): Double =
        try {
            firestore.collection(LARKAM_RECORDS)
                .whereEqualTo("userId", userId)
                .get()
                .await()
                .documents
                .sumOf { (it.get("distanceKm") as? Number)?.toDouble() ?: 0.0 }
        } catch (_: Exception) {
            0.0
        }

    companion object {
        /**
         * Larik selfie-finish Larkam yang ditulis `PresensiCameraViewModel`
         * (berisi `distanceKm`, `durationSeconds`, …). Belum ada di
         * [FirestoreCollections] karena penulisannya inline, bukan via
         * repository — baca agregat di sini menoleransi ketidakhadiran rules
         * (jatuh ke 0.0, lihat [sumDistanceKm]).
         */
        const val LARKAM_RECORDS = "larkam_records"
    }
}
