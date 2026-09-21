package com.gynda.fridaystm.data.repository

import android.content.Context
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Cache-dir storage for queued presensi JPEGs, behind an interface so the
 * offline repository unit-tests with an in-memory fake instead of files.
 *
 * Files live under `<cacheDir>/pending_presensi/` — the OS may evict them
 * under storage pressure, which [PendingPresensiSyncer] treats as a failed
 * (retryable) item rather than a crash.
 */
interface PendingPhotoCache {

    /**
     * Persists [bytes] (already-compressed JPEG) for [userId] at [timestamp].
     * @return [Result.success] with the absolute path to store in
     *   `PendingPresensiEntity.imagePath`.
     */
    fun savePendingPhoto(userId: String, timestamp: LocalDateTime, bytes: ByteArray): Result<String>

    /** Reads a previously saved file, or `null` when missing/unreadable. */
    fun readPhoto(path: String): ByteArray?

    /** Deletes a previously saved file after a successful sync. */
    fun deletePhoto(path: String): Boolean
}

/** File-backed [PendingPhotoCache] rooted at the app cache dir. */
class AppPendingPhotoCache(context: Context) : PendingPhotoCache {

    private val dir: File = File(context.applicationContext.cacheDir, DIR_NAME).apply { mkdirs() }

    private val fileNameFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

    override fun savePendingPhoto(
        userId: String,
        timestamp: LocalDateTime,
        bytes: ByteArray,
    ): Result<String> = runCatching {
        require(userId.isNotBlank()) { "userId must not be blank" }
        require(bytes.isNotEmpty()) { "photo bytes must not be empty" }
        val file = File(dir, "${userId}_${timestamp.format(fileNameFormatter)}.jpg")
        file.writeBytes(bytes)
        file.absolutePath
    }

    override fun readPhoto(path: String): ByteArray? =
        try {
            File(path).takeIf { it.isFile }?.readBytes()
        } catch (_: Exception) {
            null
        }

    override fun deletePhoto(path: String): Boolean =
        try {
            File(path).delete()
        } catch (_: Exception) {
            false
        }

    companion object {
        const val DIR_NAME = "pending_presensi"
    }
}
