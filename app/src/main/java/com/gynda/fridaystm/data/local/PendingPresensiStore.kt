package com.gynda.fridaystm.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Queue operations over pending presensi rows, behind an interface (SKILL.md
 * §9) so repositories, the syncer and ViewModels unit-test against an
 * in-memory fake instead of Room.
 */
interface PendingPresensiStore {

    /** Enqueues one capture; returns the generated row id. */
    suspend fun insert(entity: PendingPresensiEntity): Long

    /** Whole queue, oldest first. */
    suspend fun pendingList(): List<PendingPresensiEntity>

    /** Removes the row after a successful sync. */
    suspend fun deleteById(id: Int)

    /** Marks an attempt outcome ([PendingSyncStatus]) without dropping the row. */
    suspend fun updateStatus(id: Int, status: String)

    /** Atomically persists the upload receipt before any document write. */
    suspend fun checkpointUpload(id: Int, imageUrl: String) {
        throw UnsupportedOperationException("Upload checkpoint persistence is required")
    }

    /** Live count of unsynced rows for [userId]. */
    fun observePendingCount(userId: String): Flow<Int>

    /** Terminal rows retained for [userId]; legacy stores have no terminal status. */
    fun observeNeedsAttentionCount(userId: String): Flow<Int> = flowOf(0)
}

/** Room-backed [PendingPresensiStore]. Thin mapping over [PendingPresensiDao]. */
class RoomPendingPresensiStore(
    private val dao: PendingPresensiDao,
) : PendingPresensiStore {

    override suspend fun insert(entity: PendingPresensiEntity): Long =
        dao.insertPendingPresensi(entity)

    override suspend fun pendingList(): List<PendingPresensiEntity> =
        dao.getPendingPresensiList()

    override suspend fun deleteById(id: Int) =
        dao.deleteById(id)

    override suspend fun updateStatus(id: Int, status: String) =
        dao.updateStatus(id, status)

    override suspend fun checkpointUpload(id: Int, imageUrl: String) =
        dao.checkpointUpload(id, imageUrl)

    override fun observePendingCount(userId: String): Flow<Int> =
        dao.observePendingCount(userId)

    override fun observeNeedsAttentionCount(userId: String): Flow<Int> =
        dao.observeNeedsAttentionCount(userId)
}
