package com.gynda.fridaystm.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import com.gynda.fridaystm.util.IzinStatus
import com.gynda.fridaystm.util.IzinTypeValue

/**
 * Dokumen pengajuan izin/sakit — Firestore `izin_records/{autoId}`.
 *
 * Firestore DTO: semua properti punya nilai default sehingga SDK bisa
 * menginstansiasi via no-arg constructor saat `toObject<IzinRecord>()`.
 *
 * @property tipe salah satu [IzinTypeValue.SAKIT] / [IzinTypeValue.IZIN].
 * @property startDate tanggal mulai izin, format `yyyy-MM-dd`.
 * @property endDate tanggal selesai izin, format `yyyy-MM-dd`.
 * @property proofUrl download URL bukti surat di Firebase Storage
 *   (`permits/{userId}_{epochMillis}.jpg`).
 * @property status salah satu [IzinStatus]; selalu `PENDING` saat dibuat.
 */
data class IzinRecord(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val nama: String = "",
    val kelas: String = "",
    val tipe: String = IzinTypeValue.IZIN,
    val alasan: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val proofUrl: String = "",
    val status: String = IzinStatus.PENDING,
    val approvedByUid: String = "",
    val approvalNote: String = "",
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    @ServerTimestamp
    val updatedAt: Timestamp? = null,
)

/**
 * Jenis pengajuan izin — typed facade di atas [IzinTypeValue].
 */
enum class IzinType(val wireValue: String) {
    SAKIT(IzinTypeValue.SAKIT),
    IZIN(IzinTypeValue.IZIN),
}

/** [String] wire → [IzinType]; nilai tak dikenal jatuh ke `null`. */
fun izinTypeFromWire(value: String): IzinType? =
    IzinType.entries.firstOrNull { it.wireValue == value }
