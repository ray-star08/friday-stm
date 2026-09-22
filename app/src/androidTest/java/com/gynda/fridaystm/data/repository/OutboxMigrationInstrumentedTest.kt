package com.gynda.fridaystm.data.repository

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.gynda.fridaystm.data.local.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

/** Native SQLite/Room + private-files regression using synthetic bytes only. */
class OutboxMigrationInstrumentedTest {
    @Test fun privatePhotoRemainsReadableAcrossCacheEviction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val photos = AppPendingPhotoCache(context)
        val bytes = byteArrayOf(11, 22, 33)
        val path = photos.savePendingPhoto("synthetic-owner", LocalDateTime.of(2026, 9, 18, 7, 0), bytes).getOrThrow()
        try {
            java.io.File(context.cacheDir, AppPendingPhotoCache.DIR_NAME).deleteRecursively()
            assertArrayEquals("native Android path aliases must remain readable", bytes, photos.readPhoto(path))
        } finally { assertTrue(photos.deletePhoto(path)) }
    }

    @Test fun nativeVersionOneRowsSurviveVersionTwoMigration() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "synthetic-migration.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.execSQL("CREATE TABLE pending_presensi (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, userId TEXT NOT NULL, timestampIso TEXT NOT NULL, latitude REAL, longitude REAL, imagePath TEXT NOT NULL, storageFileName TEXT NOT NULL, studentName TEXT NOT NULL, studentClass TEXT NOT NULL, statusSync TEXT NOT NULL, createdAt INTEGER NOT NULL)")
            db.execSQL("INSERT INTO pending_presensi VALUES (7, 'synthetic-alice', '2026-09-18T07:00:00', -6.9, 107.5, '/legacy/photo.jpg', 'legacy.jpg', 'Fixture Alice', 'XI A', 'FAILED', 1000)")
            db.version = 1
        }
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(AppDatabase.MIGRATION_1_2).build()
        try {
            val row = database.pendingPresensiDao().getPendingPresensiList().single()
            assertEquals(7, row.id)
            assertEquals("synthetic-alice", row.userId)
            assertEquals("/legacy/photo.jpg", row.imagePath)
            assertEquals("FAILED", row.statusSync)
            assertEquals("GENERIC", row.captureKind)
            assertEquals("", row.captureId)
            assertNull(row.larkamDistanceKm)
            assertNull(row.uploadedImageUrl)
            database.pendingPresensiDao().checkpointUpload(7, "https://example.invalid/receipt.jpg")
            assertEquals("https://example.invalid/receipt.jpg", database.pendingPresensiDao().getPendingPresensiList().single().uploadedImageUrl)
        } finally { database.close(); context.deleteDatabase(name) }
    }
}
