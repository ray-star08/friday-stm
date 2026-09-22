package com.gynda.fridaystm.data.repository

import android.graphics.Bitmap
import com.gynda.fridaystm.data.local.*
import com.gynda.fridaystm.data.model.*
import com.gynda.fridaystm.util.*
import com.gynda.fridaystm.viewmodel.FakeAuthRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.LocalDateTime

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [33])
class CaptureOutboxTest {
    private val time = object : TimeProvider {
        override fun now() = LocalDateTime.of(2026, 9, 18, 7, 0)
        override fun weekOfYear() = 38
    }
    private val draft = CaptureDraft("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee", "alice", time.now(), -6.9, 107.5, "Fixture Alice", "XI A", LarkamCapture(1.25, 600, listOf(mapOf("lat" to -6.9, "lng" to 107.5))))
    private open class Queue : PendingPresensiStore {
        val rows = mutableListOf<PendingPresensiEntity>()
        override suspend fun insert(entity: PendingPresensiEntity): Long { rows += entity.copy(id = rows.size + 1); return rows.last().id.toLong() }
        override suspend fun pendingList() = rows.toList()
        override suspend fun deleteById(id: Int) { rows.removeAll { it.id == id } }
        override suspend fun updateStatus(id: Int, status: String) { val i = rows.indexOfFirst { it.id == id }; rows[i] = rows[i].copy(statusSync = status) }
        override suspend fun checkpointUpload(id: Int, imageUrl: String) { val i = rows.indexOfFirst { it.id == id }; rows[i] = rows[i].copy(uploadedImageUrl = imageUrl) }
        override fun observePendingCount(userId: String) = flowOf(rows.count { it.userId == userId })
    }
    private class Photos : PendingPhotoCache {
        val files = mutableMapOf<String, ByteArray>()
        override fun savePendingPhoto(userId: String, timestamp: LocalDateTime, bytes: ByteArray): Result<String> { val path = "photo-${files.size}"; files[path] = bytes.copyOf(); return Result.success(path) }
        override fun readPhoto(path: String) = files[path]
        override fun deletePhoto(path: String) = files.remove(path) != null
    }
    private class Storage : StorageRepository {
        var calls = 0
        override suspend fun uploadPresensiBytes(userId: String, bytes: ByteArray, storageFileName: String): Result<String> { calls++; return Result.success("https://res.cloudinary.com/fixture/image/upload/$storageFileName.jpg") }
        override suspend fun uploadPresensiSelfie(userId: String, bitmap: Bitmap, timestamp: LocalDateTime) = error("not used")
    }
    private class Legacy : PresensiRepository {
        var writes = 0
        override suspend fun savePresensi(userId: String, timestamp: LocalDateTime, imageUrl: String, lat: Double?, lng: Double?, studentName: String, studentClass: String): Result<Unit> { writes++; return Result.success(Unit) }
        override fun getPresensiHistory(userId: String) = flowOf(Result.success(emptyList<PresensiRecord>()))
    }
    @Test fun `cancellation as file save returns never leaves an untracked private photo`() = runTest {
        val auth = FakeAuthRepository(initialUid = "alice")
        val queue = Queue(); val storage = Storage(); val legacy = Legacy()
        val files = Photos()
        lateinit var submission: kotlinx.coroutines.Job
        val photos = object : PendingPhotoCache by files {
            override fun savePendingPhoto(userId: String, timestamp: LocalDateTime, bytes: ByteArray): Result<String> {
                val result = files.savePendingPhoto(userId, timestamp, bytes)
                submission.cancel()
                return result
            }
        }
        val repo = OfflineFirstPresensiRepository(auth, object : NetworkMonitor { override fun isOnline() = false }, queue, photos, storage, legacy, object : PresensiSyncScheduler { override fun schedulePresensiSync() {} }, time)
        submission = launch(start = kotlinx.coroutines.CoroutineStart.LAZY) { repo.submitCapture(draft, byteArrayOf(1,2,3)) }
        submission.start()
        submission.join()
        assertTrue(submission.isCancelled)
        assertTrue("cancelled save must not leave untracked JPEGs", files.files.keys.all { path -> queue.rows.any { it.imagePath == path } })
        assertTrue(queue.rows.all { files.readPhoto(it.imagePath) != null })
    }

    @Test fun `cancellation immediately after row deletion still removes confirmed photo`() = runTest {
        val auth = FakeAuthRepository(initialUid = "alice")
        val photos = Photos(); val storage = Storage(); val legacy = Legacy()
        lateinit var drain: kotlinx.coroutines.Job
        val queue = object : Queue() {
            override suspend fun deleteById(id: Int) {
                super.deleteById(id)
                drain.cancel()
                kotlinx.coroutines.yield()
            }
        }
        val writer = CaptureWriter { _, _ -> Result.success(Unit) }
        val repo = OfflineFirstPresensiRepository(auth, object : NetworkMonitor { override fun isOnline() = false }, queue, photos, storage, legacy, object : PresensiSyncScheduler { override fun schedulePresensiSync() {} }, time, writer)
        repo.submitCapture(draft, byteArrayOf(1,2,3)).getOrThrow()
        drain = launch(start = kotlinx.coroutines.CoroutineStart.LAZY) { PendingPresensiSyncer(auth, queue, photos, storage, legacy, writer).syncPending() }
        drain.start()
        drain.join()
        assertTrue(drain.isCancelled)
        assertTrue(queue.rows.isEmpty())
        assertTrue("confirmed capture removed from Room must not orphan its JPEG", photos.files.isEmpty())
    }

    @Test fun `failed row deletion must retain referenced photo`() = runTest {
        val auth = FakeAuthRepository(initialUid = "alice")
        val photos = Photos(); val storage = Storage(); val legacy = Legacy()
        val queue = object : Queue() {
            override suspend fun deleteById(id: Int) { throw IOException("database unavailable before delete") }
        }
        val writer = CaptureWriter { _, _ -> Result.success(Unit) }
        val repo = OfflineFirstPresensiRepository(auth, object : NetworkMonitor { override fun isOnline() = false }, queue, photos, storage, legacy, object : PresensiSyncScheduler { override fun schedulePresensiSync() {} }, time, writer)
        repo.submitCapture(draft, byteArrayOf(1,2,3)).getOrThrow()
        assertTrue(PendingPresensiSyncer(auth, queue, photos, storage, legacy, writer).syncPending().hasFailures)
        assertNotNull(photos.readPhoto(queue.rows.single().imagePath))
    }

    @Test fun `cancellation immediately after committed insert cannot delete queued evidence`() = runTest {
        val auth = FakeAuthRepository(initialUid = "alice")
        val photos = Photos(); val storage = Storage(); val legacy = Legacy()
        lateinit var submission: kotlinx.coroutines.Job
        val queue = object : Queue() {
            override suspend fun insert(entity: PendingPresensiEntity): Long {
                val id = super.insert(entity)
                submission.cancel()
                kotlinx.coroutines.yield()
                return id
            }
        }
        val repo = OfflineFirstPresensiRepository(auth, object : NetworkMonitor { override fun isOnline() = false }, queue, photos, storage, legacy, object : PresensiSyncScheduler { override fun schedulePresensiSync() {} }, time)
        submission = launch(start = kotlinx.coroutines.CoroutineStart.LAZY) { repo.submitCapture(draft, byteArrayOf(1,2,3)) }
        submission.start()
        submission.join()
        assertEquals(1, queue.rows.size)
        assertNotNull("committed Room row must retain its JPEG on cancellation", photos.readPhoto(queue.rows.single().imagePath))
    }

    @Test fun `cancellation stays cancellation and keeps queued evidence`() = runTest {
        val auth = FakeAuthRepository(initialUid = "alice")
        val queue = Queue(); val photos = Photos(); val storage = Storage(); val legacy = Legacy()
        val writer = CaptureWriter { _, _ -> throw kotlinx.coroutines.CancellationException("fixture cancellation") }
        val repo = OfflineFirstPresensiRepository(auth, object : NetworkMonitor { override fun isOnline() = true }, queue, photos, storage, legacy, object : PresensiSyncScheduler { override fun schedulePresensiSync() {} }, time, writer)
        var cancelled = false
        try { repo.submitCapture(draft, byteArrayOf(1,2,3)) } catch (_: kotlinx.coroutines.CancellationException) { cancelled = true }
        assertTrue(cancelled)
        assertEquals(1, queue.rows.size)
        assertNotNull(queue.rows.single().uploadedImageUrl)
        assertEquals(1, photos.files.size)
    }

    @Test fun `missing photo becomes terminal while uploaded receipt can still finish`() = runTest {
        val auth = FakeAuthRepository(initialUid = "alice")
        val queue = Queue(); val photos = Photos(); val storage = Storage(); val legacy = Legacy()
        var writes = 0
        val writer = CaptureWriter { _, _ -> writes++; Result.success(Unit) }
        val repo = OfflineFirstPresensiRepository(auth, object : NetworkMonitor { override fun isOnline() = false }, queue, photos, storage, legacy, object : PresensiSyncScheduler { override fun schedulePresensiSync() {} }, time, writer)
        repo.submitCapture(draft, byteArrayOf(1,2,3)).getOrThrow()
        photos.files.clear()
        val syncer = PendingPresensiSyncer(auth, queue, photos, storage, legacy, writer)
        assertFalse(syncer.syncPending().hasFailures)
        assertEquals(PendingSyncStatus.NEEDS_ATTENTION, queue.rows.single().statusSync)
        syncer.syncPending()
        assertEquals(0, storage.calls)
        queue.rows[0] = queue.rows.single().copy(uploadedImageUrl = "https://res.cloudinary.com/fixture/image/upload/existing.jpg", statusSync = PendingSyncStatus.PENDING)
        assertEquals(1, syncer.syncPending().synced)
        assertEquals(1, writes)
        assertTrue(queue.rows.isEmpty())
    }

    @Test fun `foreign account cannot submit or drain another owners evidence`() = runTest {
        val auth = FakeAuthRepository(initialUid = "alice")
        val queue = Queue(); val photos = Photos(); val storage = Storage(); val legacy = Legacy()
        val writer = CaptureWriter { _, _ -> fail("foreign write"); Result.success(Unit) }
        val repo = OfflineFirstPresensiRepository(auth, object : NetworkMonitor { override fun isOnline() = false }, queue, photos, storage, legacy, object : PresensiSyncScheduler { override fun schedulePresensiSync() {} }, time, writer)
        repo.submitCapture(draft, byteArrayOf(1,2,3)).getOrThrow()
        val original = queue.rows.toList()
        auth.emitAuthState("bob")
        assertTrue(repo.submitCapture(draft, byteArrayOf(1,2,3)).isFailure)
        PendingPresensiSyncer(auth, queue, photos, storage, legacy, writer).syncPending()
        assertEquals(original, queue.rows)
        assertEquals(0, storage.calls)
    }

    @Test fun `owner change during document read retains checkpoint for original owner retry`() = runTest {
        val auth = FakeAuthRepository(initialUid = "alice")
        val queue = Queue(); val photos = Photos(); val storage = Storage(); val legacy = Legacy()
        val documents = mutableMapOf<String, Map<String, Any?>>()
        var reads = 0
        var creates = 0
        val source = object : CaptureDocumentSource {
            override suspend fun get(collection: String, documentId: String): Map<String, Any?>? {
                reads++
                if (reads == 1) auth.emitAuthState("bob")
                return documents["$collection/$documentId"]
            }
            override suspend fun create(collection: String, documentId: String, payload: Map<String, Any?>) {
                assertEquals("alice", auth.currentUid)
                creates++
                documents["$collection/$documentId"] = payload
            }
        }
        val writer = IdempotentCaptureWriter(source) { auth.currentUid }
        val repo = OfflineFirstPresensiRepository(auth, object : NetworkMonitor { override fun isOnline() = false }, queue, photos, storage, legacy, object : PresensiSyncScheduler { override fun schedulePresensiSync() {} }, time, writer)
        repo.submitCapture(draft, byteArrayOf(1,2,3)).getOrThrow()
        val syncer = PendingPresensiSyncer(auth, queue, photos, storage, legacy, writer)

        assertEquals(PresensiSyncSummary(0, 0), syncer.syncPending())
        assertEquals("bob", auth.currentUid)
        val retained = queue.rows.single()
        val uploadedUrl = "https://res.cloudinary.com/fixture/image/upload/capture_${draft.captureId}.jpg"
        assertEquals(uploadedUrl, retained.uploadedImageUrl)
        assertArrayEquals(byteArrayOf(1,2,3), photos.readPhoto(retained.imagePath))
        assertEquals(1, storage.calls)
        assertEquals(0, creates)
        assertEquals("account changes must not make the queued capture terminal", PendingSyncStatus.PENDING, retained.statusSync)

        assertEquals(PresensiSyncSummary(0, 0), syncer.syncPending())
        assertEquals(listOf(retained), queue.rows)
        assertEquals(1, reads)
        auth.emitAuthState("alice")
        assertEquals(PresensiSyncSummary(1, 0), syncer.syncPending())
        assertEquals(1, storage.calls)
        assertEquals(2, reads)
        assertEquals(1, creates)
        assertEquals(mapOf("${FirestoreCollections.LARKAM_RECORDS}/capture_${draft.captureId}" to capturePayload(draft, uploadedUrl)), documents)
        assertEquals(0, legacy.writes)
        assertTrue(queue.rows.isEmpty())
        assertTrue(photos.files.isEmpty())
    }

    @Test fun `owner switch on exceptional document reads remains resumable without reupload`() = runTest {
        for (failOnReadback in listOf(false, true)) {
            val auth = FakeAuthRepository(initialUid = "alice")
            val queue = Queue(); val photos = Photos(); val storage = Storage(); val legacy = Legacy()
            var switched = false
            var reads = 0
            var creates = 0
            val documents = mutableMapOf<String, Map<String, Any?>>()
            val source = object : CaptureDocumentSource {
                override suspend fun get(collection: String, documentId: String): Map<String, Any?>? {
                    reads++
                    if (!switched && (!failOnReadback || reads == 2)) {
                        switched = true
                        auth.emitAuthState("bob")
                        throw com.google.firebase.firestore.FirebaseFirestoreException(
                            "owner changed during read", com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED,
                        )
                    }
                    return documents["$collection/$documentId"]
                }
                override suspend fun create(collection: String, documentId: String, payload: Map<String, Any?>) {
                    assertEquals("alice", auth.currentUid)
                    creates++
                    if (failOnReadback && creates == 1) throw com.google.firebase.firestore.FirebaseFirestoreException(
                        "uncertain create failure", com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED,
                    )
                    documents["$collection/$documentId"] = payload
                }
            }
            val writer = IdempotentCaptureWriter(source) { auth.currentUid }
            val repo = OfflineFirstPresensiRepository(auth, object : NetworkMonitor { override fun isOnline() = false }, queue, photos, storage, legacy, object : PresensiSyncScheduler { override fun schedulePresensiSync() {} }, time, writer)
            repo.submitCapture(draft, byteArrayOf(1,2,3)).getOrThrow()
            val syncer = PendingPresensiSyncer(auth, queue, photos, storage, legacy, writer)
            syncer.syncPending()
            val retained = queue.rows.single()
            assertEquals("failedReadback=$failOnReadback must retain resumable state", PendingSyncStatus.PENDING, retained.statusSync)
            assertNotNull(retained.uploadedImageUrl)
            assertNotNull(photos.readPhoto(retained.imagePath))
            assertEquals(PresensiSyncSummary(0, 0), syncer.syncPending())
            auth.emitAuthState("alice")
            assertEquals(PresensiSyncSummary(1, 0), syncer.syncPending())
            assertEquals(1, storage.calls)
            assertEquals(1, documents.size)
            assertTrue(queue.rows.isEmpty())
            assertTrue(photos.files.isEmpty())
        }
    }

    @Test fun `unchanged owner permission denial remains terminal`() = runTest {
        val auth = FakeAuthRepository(initialUid = "alice")
        val queue = Queue(); val photos = Photos(); val storage = Storage(); val legacy = Legacy()
        val source = object : CaptureDocumentSource {
            override suspend fun get(collection: String, documentId: String): Map<String, Any?>? = throw com.google.firebase.firestore.FirebaseFirestoreException(
                "policy denied", com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED,
            )
            override suspend fun create(collection: String, documentId: String, payload: Map<String, Any?>) = error("must not create")
        }
        val writer = IdempotentCaptureWriter(source) { auth.currentUid }
        val repo = OfflineFirstPresensiRepository(auth, object : NetworkMonitor { override fun isOnline() = false }, queue, photos, storage, legacy, object : PresensiSyncScheduler { override fun schedulePresensiSync() {} }, time, writer)
        repo.submitCapture(draft, byteArrayOf(1,2,3)).getOrThrow()
        PendingPresensiSyncer(auth, queue, photos, storage, legacy, writer).syncPending()
        assertEquals(PendingSyncStatus.NEEDS_ATTENTION, queue.rows.single().statusSync)
    }

    @Test fun `scheduled drain must not retry capture made terminal while waiting for online submission`() = runTest {
        val auth = FakeAuthRepository(initialUid = "alice")
        val queue = Queue(); val photos = Photos(); val storage = Storage(); val legacy = Legacy()
        val drainSnapshots = mutableListOf<List<PendingPresensiEntity>>()
        val workerQueue = object : PendingPresensiStore by queue {
            override suspend fun pendingList() = queue.pendingList().also { drainSnapshots += it }
        }
        val writerEntered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val releaseDenial = kotlinx.coroutines.CompletableDeferred<Unit>()
        val denial = com.google.firebase.firestore.FirebaseFirestoreException(
            "policy denied", com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED,
        )
        var writes = 0
        val writer = CaptureWriter { _, _ ->
            writes++
            if (writes == 1) {
                writerEntered.complete(Unit)
                releaseDenial.await()
            }
            Result.failure(denial)
        }
        val syncer = PendingPresensiSyncer(auth, workerQueue, photos, storage, legacy, writer)
        lateinit var drain: kotlinx.coroutines.Deferred<PresensiSyncSummary>
        val scheduler = object : PresensiSyncScheduler {
            override fun schedulePresensiSync() {
                assertTrue("online submission must own the outbox lock when scheduling", CaptureOutboxLock.mutex.isLocked)
                // The fake queue does not suspend: UNDISTPATCHED reaches the held mutex
                // with a PENDING snapshot before scheduling returns to the submission.
                drain = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { syncer.syncPending() }
                assertEquals(listOf(PendingSyncStatus.PENDING), drainSnapshots.map { it.single().statusSync })
                assertFalse("scheduled drain must be waiting for the submission lock", drain.isCompleted)
            }
        }
        val repo = OfflineFirstPresensiRepository(auth, object : NetworkMonitor { override fun isOnline() = true }, queue, photos, storage, legacy, scheduler, time, writer)
        val submission = async { repo.submitCapture(draft, byteArrayOf(1,2,3)) }
        writerEntered.await()
        assertTrue(CaptureOutboxLock.mutex.isLocked)
        assertFalse(submission.isCompleted)
        assertFalse(drain.isCompleted)
        assertEquals(PendingSyncStatus.PENDING, queue.rows.single().statusSync)
        assertEquals(1, writes)

        releaseDenial.complete(Unit)
        assertSame(denial, submission.await().exceptionOrNull())
        val summary = drain.await()
        assertEquals(
            "worker must reload the terminal row after its stale PENDING snapshot",
            listOf(PendingSyncStatus.PENDING, PendingSyncStatus.NEEDS_ATTENTION),
            drainSnapshots.map { it.single().statusSync },
        )
        assertEquals("alice", auth.currentUid)
        assertEquals("terminal capture must not receive a second writer call", 1, writes)
        assertEquals(PresensiSyncSummary(0, 0), summary)
        val retained = queue.rows.single()
        assertEquals(draft.captureId, retained.captureId)
        assertEquals(PendingSyncStatus.NEEDS_ATTENTION, retained.statusSync)
        assertNotNull(retained.uploadedImageUrl)
        assertEquals(setOf(retained.imagePath), photos.files.keys)
        assertArrayEquals(byteArrayOf(1,2,3), photos.readPhoto(retained.imagePath))
        assertEquals(1, storage.calls)
        assertEquals(0, legacy.writes)
    }

    @Test fun `repeat submission of pending capture reuses its row and uploaded checkpoint`() = runTest {
        val auth = FakeAuthRepository(initialUid = "alice")
        val queue = Queue(); val photos = Photos(); val storage = Storage(); val legacy = Legacy()
        var fail = true
        val writer = CaptureWriter { _, _ -> if (fail) Result.failure(IOException("disconnected")) else Result.success(Unit) }
        val repo = OfflineFirstPresensiRepository(auth, object : NetworkMonitor { override fun isOnline() = true }, queue, photos, storage, legacy, object : PresensiSyncScheduler { override fun schedulePresensiSync() {} }, time, writer)
        repo.submitCapture(draft, byteArrayOf(1,2,3)).getOrThrow()
        fail = false
        assertTrue(repo.submitCapture(draft, byteArrayOf(1,2,3)).getOrThrow() is PresensiSubmitResult.Uploaded)
        assertEquals(1, storage.calls)
        assertTrue(queue.rows.isEmpty())
        assertTrue(photos.files.isEmpty())
    }

    @Test fun `online commit failure stays queued and resumes without second media upload`() = runTest {
        val auth = FakeAuthRepository(initialUid = "alice")
        val queue = Queue(); val photos = Photos(); val storage = Storage(); val legacy = Legacy()
        var fail = true
        val writes = mutableListOf<CaptureDraft>()
        val writer = CaptureWriter { value, _ ->
            if (fail) Result.failure(IOException("offline after upload")) else { writes += value; Result.success(Unit) }
        }
        val repo = OfflineFirstPresensiRepository(auth, object : NetworkMonitor { override fun isOnline() = true }, queue, photos, storage, legacy, object : PresensiSyncScheduler { override fun schedulePresensiSync() {} }, time, writer)
        assertEquals(PresensiSubmitResult.QueuedOffline, repo.submitCapture(draft, byteArrayOf(1,2,3)).getOrThrow())
        assertEquals(1, queue.rows.size)
        assertNotNull(queue.rows.single().uploadedImageUrl)
        fail = false
        val summary = PendingPresensiSyncer(auth, queue, photos, storage, legacy, writer).syncPending()
        assertEquals(1, summary.synced)
        assertEquals(listOf(draft), writes)
        assertEquals(1, storage.calls)
        assertEquals(0, legacy.writes)
        assertTrue(queue.rows.isEmpty())
    }

    @Test fun `typed Larkam drains through its writer with every field intact`() = runTest {
        val auth = FakeAuthRepository(initialUid = "alice")
        val queue = Queue(); val photos = Photos(); val storage = Storage(); val legacy = Legacy()
        val writes = mutableListOf<CaptureDraft>()
        val writer = CaptureWriter { value, _ -> writes += value; Result.success(Unit) }
        val repo = OfflineFirstPresensiRepository(auth, object : NetworkMonitor { override fun isOnline() = false }, queue, photos, storage, legacy, object : PresensiSyncScheduler { override fun schedulePresensiSync() {} }, time, writer)
        repo.submitCapture(draft, byteArrayOf(1,2,3)).getOrThrow()
        val result = PendingPresensiSyncer(auth, queue, photos, storage, legacy, writer).syncPending()
        assertEquals(listOf(draft), writes)
        assertEquals(1, result.synced)
        assertEquals(0, legacy.writes)
        assertTrue(queue.rows.isEmpty())
        assertTrue(photos.files.isEmpty())
    }

    @Test fun `offline Larkam is not replayed as generic presensi`() = runTest {
        val auth = FakeAuthRepository(initialUid = "alice")
        val queue = Queue(); val photos = Photos(); val storage = Storage(); val legacy = Legacy()
        val repo = OfflineFirstPresensiRepository(auth, object : NetworkMonitor { override fun isOnline() = false }, queue, photos, storage, legacy, object : PresensiSyncScheduler { override fun schedulePresensiSync() {} }, time)
        assertEquals(PresensiSubmitResult.QueuedOffline, repo.submitCapture(draft, byteArrayOf(1,2,3)).getOrThrow())
        PendingPresensiSyncer(auth, queue, photos, storage, legacy).syncPending()
        assertEquals("Larkam cannot silently become generic presensi", 0, legacy.writes)
        assertEquals("missing typed writer must retain the evidence", 1, queue.rows.size)
    }
}
