package com.gynda.fridaystm.data.repository

import android.graphics.Bitmap
import com.gynda.fridaystm.data.local.PendingPresensiEntity
import com.gynda.fridaystm.data.local.PendingPresensiStore
import com.gynda.fridaystm.data.local.PendingSyncStatus
import com.gynda.fridaystm.data.model.PresensiRecord
import com.gynda.fridaystm.util.NetworkMonitor
import com.gynda.fridaystm.util.PresensiSyncScheduler
import com.gynda.fridaystm.util.TimeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDateTime

/**
 * Unit tests for the offline-first presensi flow on pure-JVM fakes: no Room,
 * no WorkManager, no Firebase, no Robolectric.
 *
 * - [OfflineFirstPresensiRepository]: online uploads + saves; offline (or a
 *   mid-upload network drop) persists to the queue and schedules the worker.
 * - [PendingPresensiSyncer]: the worker's drain logic (upload → save →
 *   delete row + file; failures flip to `FAILED`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PresensiRepositoryTest {

    private val fixedTime: LocalDateTime = LocalDateTime.of(2026, 9, 6, 8, 20, 57)
    private val fakeBytes = byteArrayOf(1, 2, 3, 4)

    private class FakeNetworkMonitor(var online: Boolean) : NetworkMonitor {
        override fun isOnline(): Boolean = online
    }

    private class FakePendingPresensiStore : PendingPresensiStore {
        private val rows = mutableListOf<PendingPresensiEntity>()
        private var nextId = 1

        override suspend fun insert(entity: PendingPresensiEntity): Long {
            rows += entity.copy(id = nextId++)
            return rows.last().id.toLong()
        }

        override suspend fun pendingList(): List<PendingPresensiEntity> = rows.toList()

        override suspend fun deleteById(id: Int) {
            rows.removeAll { it.id == id }
        }

        override suspend fun updateStatus(id: Int, status: String) {
            val index = rows.indexOfFirst { it.id == id }
            if (index >= 0) rows[index] = rows[index].copy(statusSync = status)
        }

        override fun observePendingCount(userId: String): Flow<Int> =
            MutableStateFlow(rows.count { it.userId == userId })

        val size: Int get() = rows.size
    }

    private class FakePendingPhotoCache : PendingPhotoCache {
        val files = mutableMapOf<String, ByteArray>()
        var saveCalls = 0

        override fun savePendingPhoto(userId: String, timestamp: LocalDateTime, bytes: ByteArray): Result<String> {
            saveCalls++
            val path = "/cache/pending_presensi/${userId}_${timestamp}.jpg"
            files[path] = bytes
            return Result.success(path)
        }

        override fun readPhoto(path: String): ByteArray? = files[path]

        override fun deletePhoto(path: String): Boolean = files.remove(path) != null
    }

    private class FakeStorageRepository(
        var failWith: Throwable? = null,
    ) : StorageRepository {
        var uploadBytesCalls = 0
        var lastFileName: String? = null

        override suspend fun uploadPresensiSelfie(
            userId: String,
            bitmap: Bitmap,
            timestamp: LocalDateTime,
        ): Result<String> = Result.failure(IllegalStateException("Bitmap path unused in these tests"))

        override suspend fun uploadPresensiBytes(
            userId: String,
            bytes: ByteArray,
            storageFileName: String,
        ): Result<String> {
            uploadBytesCalls++
            lastFileName = storageFileName
            failWith?.let { return Result.failure(it) }
            return Result.success("https://storage.example.com/$storageFileName")
        }
    }

    private class FakePresensiRepository(
        var shouldFail: Boolean = false,
    ) : PresensiRepository {
        var saveCalls = 0
        var lastImageUrl: String? = null

        override suspend fun savePresensi(
            userId: String,
            timestamp: LocalDateTime,
            imageUrl: String,
            lat: Double?,
            lng: Double?,
            studentName: String,
            studentClass: String,
        ): Result<Unit> {
            saveCalls++
            lastImageUrl = imageUrl
            return if (shouldFail) Result.failure(IllegalStateException("firestore failed"))
            else Result.success(Unit)
        }

        override fun getPresensiHistory(userId: String): Flow<Result<List<PresensiRecord>>> =
            kotlinx.coroutines.flow.flowOf(Result.success(emptyList()))
    }

    private class FakeSyncScheduler : PresensiSyncScheduler {
        var scheduleCalls = 0
        override fun schedulePresensiSync() {
            scheduleCalls++
        }
    }

    private inner class FakeTime : TimeProvider {
        override fun now(): LocalDateTime = fixedTime
        override fun weekOfYear(): Int = 36
    }

    private inner class Fixture(
        val network: FakeNetworkMonitor = FakeNetworkMonitor(online = true),
        val queue: FakePendingPresensiStore = FakePendingPresensiStore(),
        val photos: FakePendingPhotoCache = FakePendingPhotoCache(),
        val storage: FakeStorageRepository = FakeStorageRepository(),
        val presensi: FakePresensiRepository = FakePresensiRepository(),
        val scheduler: FakeSyncScheduler = FakeSyncScheduler(),
    ) {
        val repo = OfflineFirstPresensiRepository(
            networkMonitor = network,
            queue = queue,
            photoCache = photos,
            storageRepository = storage,
            presensiRepository = presensi,
            syncScheduler = scheduler,
            timeProvider = FakeTime(),
        )
        val syncer = PendingPresensiSyncer(queue, photos, storage, presensi)

        suspend fun submit(): Result<PresensiSubmitResult> = repo.submitPresensi(
            userId = "user123",
            timestamp = fixedTime,
            imageBytes = fakeBytes,
            lat = -6.8868,
            lng = 107.5381,
            studentName = "Budi",
            studentClass = "XI RPL 1",
        )
    }

    @Test
    fun submitPresensi_offline_savesToRoomAndEnqueuesWorker() = runTest {
        val f = Fixture(network = FakeNetworkMonitor(online = false))

        val result = f.submit()

        assertTrue(result.isSuccess)
        assertEquals(PresensiSubmitResult.QueuedOffline, result.getOrThrow())

        // JPEG cached to a file …
        assertEquals(1, f.photos.saveCalls)
        // … metadata enqueued as PENDING …
        assertEquals(1, f.queue.size)
        val row = f.queue.pendingList().single()
        assertEquals("user123", row.userId)
        assertEquals(PendingSyncStatus.PENDING, row.statusSync)
        assertTrue(row.imagePath.isNotBlank())
        assertTrue(row.storageFileName.startsWith("presensi_selfies/user123/"))
        assertEquals("Budi", row.studentName)
        assertEquals("XI RPL 1", row.studentClass)
        // … and the constrained sync worker scheduled exactly once.
        assertEquals(1, f.scheduler.scheduleCalls)

        // Nothing touched the network.
        assertEquals(0, f.storage.uploadBytesCalls)
        assertEquals(0, f.presensi.saveCalls)
    }

    @Test
    fun submitPresensi_online_uploadsAndSavesWithoutQueueing() = runTest {
        val f = Fixture()

        val result = f.submit()

        assertTrue(result.isSuccess)
        val uploaded = result.getOrThrow() as PresensiSubmitResult.Uploaded
        assertTrue(uploaded.imageUrl.contains("presensi_selfies/user123/"))

        assertEquals(1, f.storage.uploadBytesCalls)
        assertEquals(1, f.presensi.saveCalls)
        assertEquals(uploaded.imageUrl, f.presensi.lastImageUrl)
        assertEquals(0, f.queue.size)
        assertEquals(0, f.photos.saveCalls)
        assertEquals(0, f.scheduler.scheduleCalls)
    }

    @Test
    fun submitPresensi_onlineNetworkFailure_queuesOffline() = runTest {
        val f = Fixture(storage = FakeStorageRepository(failWith = IOException("Network unreachable")))
        // Signal dropped between the online check and the put.
        f.network.online = true

        val result = f.submit()

        assertTrue(result.isSuccess)
        assertEquals(PresensiSubmitResult.QueuedOffline, result.getOrThrow())
        assertEquals(1, f.queue.size)
        assertEquals(1, f.scheduler.scheduleCalls)
        assertEquals(0, f.presensi.saveCalls)
    }

    @Test
    fun submitPresensi_onlineNonNetworkFailure_returnsFailureWithoutQueueing() = runTest {
        val f = Fixture(presensi = FakePresensiRepository(shouldFail = true))

        val result = f.submit()

        assertTrue(result.isFailure)
        assertEquals(0, f.queue.size)
        assertEquals(0, f.scheduler.scheduleCalls)
    }

    @Test
    fun syncPending_uploadsWritesAndCleansUp() = runTest {
        val f = Fixture(network = FakeNetworkMonitor(online = false))
        f.submit()
        f.network.online = true
        val imagePath = f.queue.pendingList().single().imagePath

        val summary = f.syncer.syncPending()

        assertEquals(PresensiSyncSummary(synced = 1, failed = 0), summary)
        assertEquals(0, f.queue.size)
        assertEquals(null, f.photos.readPhoto(imagePath))
        assertEquals(1, f.storage.uploadBytesCalls)
        assertEquals(1, f.presensi.saveCalls)
        assertEquals(
            "https://storage.example.com/${f.storage.lastFileName}",
            f.presensi.lastImageUrl,
        )
    }

    @Test
    fun syncPending_failedUpload_marksFailedAndKeepsRow() = runTest {
        val f = Fixture(
            network = FakeNetworkMonitor(online = false),
            storage = FakeStorageRepository(failWith = IOException("still offline")),
        )
        f.submit()

        val summary = f.syncer.syncPending()

        assertEquals(PresensiSyncSummary(synced = 0, failed = 1), summary)
        assertEquals(1, f.queue.size)
        assertEquals(
            PendingSyncStatus.FAILED,
            f.queue.pendingList().single().statusSync,
        )
        // Cache file kept for the backoff retry.
        assertEquals(1, f.photos.files.size)
        assertEquals(0, f.presensi.saveCalls)
    }

    @Test
    fun observePendingCount_reflectsQueue() = runTest {
        val f = Fixture(network = FakeNetworkMonitor(online = false))
        f.submit()

        var count = -1
        val job = launch {
            f.queue.observePendingCount("user123").collect { count = it }
        }
        runCurrent()
        assertEquals(1, count)
        job.cancel()
    }
}
