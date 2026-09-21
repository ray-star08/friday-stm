package com.gynda.fridaystm.data.local

import kotlinx.coroutines.flow.Flow

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

    /** Live count of unsynced rows for [userId]. */
    fun observePendingCount(userId: String): Flow<Int>
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

    override fun observePendingCount(userId: String): Flow<Int> =
        dao.observePendingCount(userId)
}
