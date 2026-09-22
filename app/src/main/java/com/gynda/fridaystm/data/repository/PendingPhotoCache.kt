package com.gynda.fridaystm.data.repository

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime

/** Private durable JPEG storage; legacy cache paths remain readable during migration. */
interface PendingPhotoCache {
    fun savePendingPhoto(userId: String, timestamp: LocalDateTime, bytes: ByteArray): Result<String>
    fun readPhoto(path: String): ByteArray?
    fun deletePhoto(path: String): Boolean
}

class AppPendingPhotoCache(context: Context) : PendingPhotoCache {
    private val dir = File(context.applicationContext.noBackupFilesDir, DIR_NAME)
    private val legacyDir = File(context.applicationContext.cacheDir, DIR_NAME)

    override fun savePendingPhoto(userId: String, timestamp: LocalDateTime, bytes: ByteArray): Result<String> = runCatching {
        require(userId.isNotBlank()) { "userId must not be blank" }
        require(bytes.isNotEmpty()) { "photo bytes must not be empty" }
        check(dir.isDirectory || dir.mkdirs()) { "Cannot create private capture directory" }
        val file = File.createTempFile("capture_", ".jpg", dir)
        try {
            FileOutputStream(file).use { output -> output.write(bytes); output.fd.sync() }
            file.absolutePath
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }

    /** No arbitrary private files, path traversal or symbolic-link escapes. */
    private fun allowedFile(path: String): File? = runCatching {
        val file = File(path)
        require(file.isAbsolute)
        val normalized = file.toPath().normalize().toFile()
        val canonical = file.canonicalFile
        // Android itself aliases /data/user/0 through /data/data. Canonicalize
        // the trusted parent too, but reject a symlink for the evidence file.
        require(java.nio.file.Files.isSymbolicLink(normalized.toPath()).not())
        require(canonical.parentFile == dir.canonicalFile || canonical.parentFile == legacyDir.canonicalFile)
        require(canonical.name.endsWith(".jpg"))
        canonical
    }.getOrNull()

    override fun readPhoto(path: String): ByteArray? = runCatching {
        allowedFile(path)?.takeIf { it.isFile }?.readBytes()
    }.getOrNull()

    override fun deletePhoto(path: String): Boolean = runCatching {
        allowedFile(path)?.takeIf { it.isFile }?.delete() ?: false
    }.getOrDefault(false)

    companion object { const val DIR_NAME = "pending_presensi" }
}
