package com.gynda.fridaystm.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Local database for the offline presensi queue (v1 — single table).
 *
 * **Manual provider, not Hilt/Koin:** this codebase wires dependencies by
 * hand through `ViewModel.factory(...)` call sites (see `AppNavHost`), so a
 * singleton accessor here matches the existing architecture without dragging
 * in a DI framework + its Gradle plugins for one database. If the project
 * later adopts Hilt/Koin, replace [get] with the framework's singleton
 * binding — the DAO/repository seams stay unchanged.
 *
 * **Migrations:** v1 is the initial schema, so no `Migration` object is
 * needed yet. When v2 adds/renames a column, add the `Migration(1, 2)` here
 * and bump [version] — never fall back to `fallbackToDestructiveMigration()`
 * in production, or queued (unsynced) presensi rows would be wiped.
 */
@Database(
    entities = [PendingPresensiEntity::class],
    version = AppDatabase.VERSION,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun pendingPresensiDao(): PendingPresensiDao

    companion object {
        const val VERSION = 1
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
                ).build().also { instance = it }
            }
    }
}
