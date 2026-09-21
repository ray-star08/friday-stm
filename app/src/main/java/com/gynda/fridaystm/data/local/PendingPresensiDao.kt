package com.gynda.fridaystm.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Persistence for the offline presensi queue (table `pending_presensi`).
 *
 * All one-shot calls are `suspend` (Room moves them off the main thread);
 * the Dashboard banner observes [observePendingCount] as a `Flow`, so it
 * updates the moment an item is enqueued or drained — no manual refresh.
 */
@Dao
interface PendingPresensiDao {

    /** Enqueues one capture; returns the generated row id. */
    @Insert
    suspend fun insertPendingPresensi(entity: PendingPresensiEntity): Long

    /** Whole queue, oldest first — the order [PresensiSyncWorker][com.gynda.fridaystm.worker.PresensiSyncWorker] drains it. */
    @Query("SELECT * FROM pending_presensi ORDER BY createdAt ASC")
    suspend fun getPendingPresensiList(): List<PendingPresensiEntity>

    /** Removes the row (and the caller deletes the cache file) after a successful sync. */
    @Query("DELETE FROM pending_presensi WHERE id = :id")
    suspend fun deleteById(id: Int)

    /**
     * Marks an attempt outcome without dropping the row — [status] is one of
     * [PendingSyncStatus]. Failed rows stay queued for the backoff retry.
     */
    @Query("UPDATE pending_presensi SET statusSync = :status WHERE id = :id")
    suspend fun updateStatus(id: Int, status: String)

    /** Live count of unsynced rows for [userId] — drives the Dashboard banner. */
    @Query("SELECT COUNT(*) FROM pending_presensi WHERE userId = :userId")
    fun observePendingCount(userId: String): Flow<Int>
}
