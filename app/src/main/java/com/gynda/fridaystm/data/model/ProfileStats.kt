package com.gynda.fridaystm.data.model

/**
 * Ringkasan statistik siswa untuk layar Profil — agregat lintas koleksi.
 *
 * @property presensiCount total presensi hadir (dokumen `presensi_records`).
 * @property larkamDistanceKm total jarak lari (sum `distanceKm`, `larkam_records`).
 * @property izinCount total berkas pengajuan (`izin_records`).
 */
data class ProfileStats(
    val presensiCount: Int = 0,
    val larkamDistanceKm: Double = 0.0,
    val izinCount: Int = 0,
)
