package com.gynda.fridaystm.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import androidx.camera.core.ImageProxy
import android.location.Location
import com.gynda.fridaystm.data.model.CaptureDraft

/** Owns bitmap processing and releases every bitmap once JPEG bytes have been produced. */
fun processCaptureBitmap(bitmap: Bitmap, draft: CaptureDraft): ByteArray {
    var marked: Bitmap? = null
    try {
        val location = if (draft.lat != null && draft.lng != null) Location("capture").apply {
            latitude = draft.lat
            longitude = draft.lng
        } else null
        marked = draft.larkam?.let { run ->
            val duration = java.lang.String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", run.durationSeconds / 3600, (run.durationSeconds % 3600) / 60, run.durationSeconds % 60)
            addLarkamWatermark(bitmap, draft.studentName, draft.studentClass, location, draft.timestamp, run.distanceKm.toFloat(), duration)
        } ?: addPresensiWatermark(bitmap, draft.studentName, draft.studentClass, location, draft.timestamp)
        return marked.toCompressJpegByteArray(80).also { check(it.isNotEmpty()) { "Foto kosong" } }
    } finally {
        marked?.let { if (!it.isRecycled) it.recycle() }
        if (!bitmap.isRecycled) bitmap.recycle()
    }
}

/** Decode only real CameraX JPEG evidence. The caller owns and closes the proxy. */
fun decodeCaptureBitmap(proxy: ImageProxy): Bitmap {
    check(proxy.format == ImageFormat.JPEG) { "Format foto tidak didukung" }
    val buffer = proxy.planes.firstOrNull()?.buffer?.duplicate()
        ?: error("Data foto tidak tersedia")
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: error("Foto tidak dapat dibaca. Ambil foto ulang.")
    val rotation = proxy.imageInfo.rotationDegrees
    if (rotation == 0) return bitmap
    return try {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height,
            android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }, true).also {
            if (it !== bitmap) bitmap.recycle()
        }
    } catch (error: Exception) {
        bitmap.recycle()
        throw error
    }
}
