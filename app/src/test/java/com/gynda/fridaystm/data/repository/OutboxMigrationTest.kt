package com.gynda.fridaystm.data.repository

import android.database.sqlite.SQLiteDatabase
import com.gynda.fridaystm.data.local.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OutboxMigrationTest {
    @Test fun `installed v1 queue survives application database upgrade`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("friday_stm.db")
        val path = context.getDatabasePath("friday_stm.db")
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.execSQL("CREATE TABLE pending_presensi (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, userId TEXT NOT NULL, timestampIso TEXT NOT NULL, latitude REAL, longitude REAL, imagePath TEXT NOT NULL, storageFileName TEXT NOT NULL, studentName TEXT NOT NULL, studentClass TEXT NOT NULL, statusSync TEXT NOT NULL, createdAt INTEGER NOT NULL)")
            db.execSQL("INSERT INTO pending_presensi VALUES (7, 'alice', '2026-09-18T07:00:00', -6.9, 107.5, '/legacy/photo.jpg', 'legacy.jpg', 'Fixture Alice', 'XI A', 'FAILED', 1000)")
            db.version = 1
        }
        val database = AppDatabase.get(context)
        try {
            val result = runCatching { database.pendingPresensiDao().getPendingPresensiList() }
            assertTrue("Non-destructive migration must open existing queue: ${result.exceptionOrNull()}", result.isSuccess)
            val row = result.getOrThrow().single()
            assertEquals(7, row.id)
            assertEquals("alice", row.userId)
            assertEquals("/legacy/photo.jpg", row.imagePath)
            assertEquals("FAILED", row.statusSync)
            assertEquals("GENERIC", row.captureKind)
            assertEquals("", row.captureId)
            assertNull(row.larkamDistanceKm)
            assertNull(row.uploadedImageUrl)
            database.pendingPresensiDao().checkpointUpload(7, "https://example.invalid/receipt.jpg")
            assertEquals("https://example.invalid/receipt.jpg", database.pendingPresensiDao().getPendingPresensiList().single().uploadedImageUrl)
        } finally { database.close() }
    }
}
