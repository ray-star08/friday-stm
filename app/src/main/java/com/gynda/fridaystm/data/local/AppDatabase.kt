package com.gynda.fridaystm.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Local database for the durable capture outbox (v2 — single table).
 *
 * **Manual provider, not Hilt/Koin:** this codebase wires dependencies by
 * hand through `ViewModel.factory(...)` call sites (see `AppNavHost`), so a
 * singleton accessor here matches the existing architecture without dragging
 * in a DI framework + its Gradle plugins for one database. If the project
 * later adopts Hilt/Koin, replace [get] with the framework's singleton
 * binding — the DAO/repository seams stay unchanged.
 *
 * Version 2 adds typed metadata and upload checkpoints without dropping any v1 row.
 * Legacy rows keep their capture timestamp/file and are generic evidence; missing
 * Larkam metadata cannot be reconstructed. Never use destructive migration.
 */
@Database(
    entities = [PendingPresensiEntity::class],
    version = AppDatabase.VERSION,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun pendingPresensiDao(): PendingPresensiDao

    companion object {
        const val VERSION = 2
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pending_presensi ADD COLUMN captureId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE pending_presensi ADD COLUMN captureKind TEXT NOT NULL DEFAULT 'GENERIC'")
                db.execSQL("ALTER TABLE pending_presensi ADD COLUMN larkamDistanceKm REAL")
                db.execSQL("ALTER TABLE pending_presensi ADD COLUMN larkamDurationSeconds INTEGER")
                db.execSQL("ALTER TABLE pending_presensi ADD COLUMN larkamRoute TEXT")
                db.execSQL("ALTER TABLE pending_presensi ADD COLUMN uploadedImageUrl TEXT")
                db.execSQL("ALTER TABLE pending_presensi ADD COLUMN lastError TEXT")
            }
        }
        private const val NAME = "friday_stm.db"

        @Volatile
        private var instance: AppDatabase? = null

        /**
         * Process-wide singleton. Takes any [Context] (the application context
         * is used internally, so callers cannot leak an Activity).
         */
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    NAME,
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
