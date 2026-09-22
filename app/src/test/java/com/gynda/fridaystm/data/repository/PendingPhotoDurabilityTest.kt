package com.gynda.fridaystm.data.repository

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PendingPhotoDurabilityTest {
    @Test fun `same second captures survive cache eviction independently`() {
        val context: Context = RuntimeEnvironment.getApplication()
        val cache = AppPendingPhotoCache(context)
        val time = LocalDateTime.of(2026, 9, 18, 7, 0)
        val first = cache.savePendingPhoto("alice", time, byteArrayOf(1)).getOrThrow()
        val second = cache.savePendingPhoto("alice", time, byteArrayOf(2)).getOrThrow()
        assertNotEquals(first, second)
        assertTrue(File(first).canonicalPath.startsWith(context.noBackupFilesDir.canonicalPath + "/"))
        File(context.cacheDir, AppPendingPhotoCache.DIR_NAME).deleteRecursively()
        assertArrayEquals(byteArrayOf(1), cache.readPhoto(first))
        assertArrayEquals(byteArrayOf(2), cache.readPhoto(second))
        cache.deletePhoto(first); cache.deletePhoto(second)
    }
    @Test fun `legacy photos readable but arbitrary private paths forbidden`() {
        val context: Context = RuntimeEnvironment.getApplication()
        val cache = AppPendingPhotoCache(context)
        val legacy = File(File(context.cacheDir, AppPendingPhotoCache.DIR_NAME).apply { mkdirs() }, "fixture.jpg").apply { writeBytes(byteArrayOf(9)) }
        val foreign = File(context.filesDir, "private.jpg").apply { writeBytes(byteArrayOf(8)) }
        assertArrayEquals(byteArrayOf(9), cache.readPhoto(legacy.path))
        assertNull(cache.readPhoto(foreign.path))
        assertFalse(cache.deletePhoto(foreign.path))
        assertTrue(foreign.exists())
        legacy.delete(); foreign.delete()
    }
}
