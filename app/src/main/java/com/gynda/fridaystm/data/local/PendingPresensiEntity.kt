package com.gynda.fridaystm.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Wire values for [PendingPresensiEntity.statusSync].
 *
 * The worker flips an item to [FAILED] when an attempt fails (the file is
 * kept, so a later run or the exponential-backoff retry can pick it up
 * again); successful items are deleted outright.
 */
object PendingSyncStatus {
    const val PENDING = "PENDING"
    const val FAILED = "FAILED"
}

/**
 * One presensi capture waiting for connectivity — Room table `pending_presensi`.
 *
 * Stored when the device is offline (or the upload throws a network error):
 * the watermarked JPEG lives at [imagePath] in the app cache dir, and this
 * row carries everything [PendingPresensiSyncer][com.gynda.fridaystm.data.repository.PendingPresensiSyncer]
 * needs to finish the Firebase Storage upload + Firestore write later.
 *
 * `studentName`/`studentClass` are snapshotted (not re-read from `users/`)
 * so the queued write is identical to the online one even if the profile
 * changes before the sync runs.
 *
 * @property timestampIso capture time, `ISO_LOCAL_DATE_TIME` (re-parsed on sync).
 * @property storageFileName intended Storage object name, e.g.
 *   `presensi_selfies/{uid}/{yyyyMMdd_HHmmss}.jpg` — fixed at enqueue time so
 *   retries upload to the same path instead of scattering duplicates.
 * @property createdAt enqueue time, epoch-millis (queue ordering).
 */
@Entity(tableName = "pending_presensi")
data class PendingPresensiEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val userId: String,
    val timestampIso: String,
    val latitude: Double?,
    val longitude: Double?,
    val imagePath: String,
    val storageFileName: String,
    val studentName: String,
    val studentClass: String,
    val statusSync: String = PendingSyncStatus.PENDING,
    val createdAt: Long,
)
