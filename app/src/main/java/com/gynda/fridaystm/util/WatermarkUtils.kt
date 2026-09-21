package com.gynda.fridaystm.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.location.Location
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val MAX_WIDTH = 1280
private val TIMESTAMP_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'WIB'")

/**
 * Adds presensi watermark efficiently with OOM guard.
 *
 * - Scales down if width > 1280px via [Bitmap.createScaledBitmap]
 * - Draws semi-transparent band (black 60% alpha) at bottom
 * - Renders 3 rows with anti-aliased [Paint]
 * - Recycles intermediate bitmaps when a new one is created
 */
fun addPresensiWatermark(
    bitmap: Bitmap,
    studentName: String,
    studentClass: String,
    location: Location?,
    timestamp: LocalDateTime,
): Bitmap {
    // 1. Early scaling to bound memory.
    var working: Bitmap = bitmap
    if (bitmap.width > MAX_WIDTH) {
        val ratio = MAX_WIDTH.toFloat() / bitmap.width
        val newHeight = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, MAX_WIDTH, newHeight, true)
        if (scaled !== bitmap && !bitmap.isRecycled) {
            bitmap.recycle()
        }
        working = scaled
    }

    // Ensure mutable ARGB_8888 for Canvas drawing.
    if (!working.isMutable) {
        val copy = working.copy(Bitmap.Config.ARGB_8888, true)
        if (copy != null && copy !== working) {
            if (!working.isRecycled) working.recycle()
            working = copy
        }
    } else if (working.config != Bitmap.Config.ARGB_8888) {
        // Force ARGB_8888 even if mutable but different config.
        val copy = working.copy(Bitmap.Config.ARGB_8888, true)
        if (copy != null && copy !== working) {
            if (!working.isRecycled) working.recycle()
            working = copy
        }
    }

    val result = working
    val canvas = Canvas(result)
    val width = result.width
    val height = result.height

    // Band sizing proportional to width.
    val textSize = (width * 0.032f).coerceIn(22f, 42f)
    val padding = (width * 0.024f).coerceIn(12f, 24f)
    val lineHeight = textSize * 1.45f
    val bandHeight = lineHeight * 3 + padding * 2.2f

    // Background band 60% black -> alpha 153/255.
    val bgPaint = Paint().apply {
        color = Color.argb(153, 0, 0, 0)
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    canvas.drawRect(
        0f,
        height - bandHeight,
        width.toFloat(),
        height.toFloat(),
        bgPaint
    )

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        this.textSize = textSize
        isAntiAlias = true
        // Slight shadow for legibility on varied photos.
        setShadowLayer(2f, 1f, 1f, Color.argb(120, 0, 0, 0))
    }

    val row1 = "$studentName \u2022 $studentClass"
    val row2 = timestamp.format(TIMESTAMP_FORMATTER)
    val row3 = if (location != null) {
        "Lat: %.4f, Long: %.4f".format(location.latitude, location.longitude)
    } else {
        "Lokasi tidak tersedia"
    }

    // Available width for text.
    val maxTextWidth = width - padding * 2

    fun drawRow(text: String, baselineY: Float) {
        var t = text
        // Simple ellipsize if exceeds width.
        if (textPaint.measureText(t) > maxTextWidth) {
            val ellipsis = "\u2026"
            // Binary truncate for speed.
            var end = t.length
            while (end > 0 && textPaint.measureText(t.substring(0, end) + ellipsis) > maxTextWidth) {
                end--
            }
            t = if (end > 0) t.substring(0, end) + ellipsis else ellipsis
        }
        canvas.drawText(t, padding, baselineY, textPaint)
    }

    // Baseline calculations using FontMetrics.
    val fm = textPaint.fontMetrics
    // top of first line = height - bandHeight + padding
    // baseline = top - ascent (ascent is negative)
    val firstBaseline = height - bandHeight + padding - fm.ascent
    val secondBaseline = firstBaseline + lineHeight
    val thirdBaseline = secondBaseline + lineHeight

    drawRow(row1, firstBaseline)
    drawRow(row2, secondBaseline)
    drawRow(row3, thirdBaseline)

    // Clear shadow to avoid leaking state.
    textPaint.clearShadowLayer()

    return result
}

/**
 * Compresses this [Bitmap] to JPEG [ByteArray].
 * @param quality 0..100, default 80
 */
fun Bitmap.toCompressJpegByteArray(quality: Int = 80): ByteArray {
    val q = quality.coerceIn(0, 100)
    val stream = ByteArrayOutputStream()
    // Use JPEG for photo size efficiency.
    compress(Bitmap.CompressFormat.JPEG, q, stream)
    return stream.toByteArray()
}

/**
 * Larkam watermark with live stats (distance + duration).
 * Efficient: same 1280 scaling + semi-transparent band + anti-alias 3 rows.
 */
fun addLarkamWatermark(
    bitmap: Bitmap,
    studentName: String,
    studentClass: String,
    location: Location?,
    timestamp: LocalDateTime,
    totalDistanceKm: Float,
    durationFormatted: String,
): Bitmap {
    var working: Bitmap = bitmap
    if (bitmap.width > MAX_WIDTH) {
        val ratio = MAX_WIDTH.toFloat() / bitmap.width
        val newHeight = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, MAX_WIDTH, newHeight, true)
        if (scaled !== bitmap && !bitmap.isRecycled) bitmap.recycle()
        working = scaled
    }
    if (!working.isMutable) {
        val copy = working.copy(Bitmap.Config.ARGB_8888, true)
        if (copy != null && copy !== working) {
            if (!working.isRecycled) working.recycle()
            working = copy
        }
    } else if (working.config != Bitmap.Config.ARGB_8888) {
        val copy = working.copy(Bitmap.Config.ARGB_8888, true)
        if (copy != null && copy !== working) {
            if (!working.isRecycled) working.recycle()
            working = copy
        }
    }
    val result = working
    val canvas = Canvas(result)
    val width = result.width
    val height = result.height
    val textSize = (width * 0.032f).coerceIn(22f, 42f)
    val padding = (width * 0.024f).coerceIn(12f, 24f)
    val lineHeight = textSize * 1.45f
    val bandHeight = lineHeight * 3 + padding * 2.2f
    val bgPaint = Paint().apply {
        color = Color.argb(153, 0, 0, 0)
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    canvas.drawRect(0f, height - bandHeight, width.toFloat(), height.toFloat(), bgPaint)
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        this.textSize = textSize
        isAntiAlias = true
        setShadowLayer(2f, 1f, 1f, Color.argb(120, 0, 0, 0))
    }
    val row1 = "$studentName \u2022 $studentClass | LARKAM (LARI KAMPUS)"
    val row2 = "%.2f KM | %s".format(totalDistanceKm, durationFormatted)
    val row3 = if (location != null) {
        "Lat: %.4f, Long: %.4f | %s".format(
            location.latitude, location.longitude,
            timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'WIB'"))
        )
    } else {
        "Lokasi tidak tersedia | %s".format(timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'WIB'")))
    }
    val maxTextWidth = width - padding * 2
    fun drawRow(text: String, baselineY: Float) {
        var t = text
        if (textPaint.measureText(t) > maxTextWidth) {
            val ellipsis = "\u2026"
            var end = t.length
            while (end > 0 && textPaint.measureText(t.substring(0, end) + ellipsis) > maxTextWidth) end--
            t = if (end > 0) t.substring(0, end) + ellipsis else ellipsis
        }
        canvas.drawText(t, padding, baselineY, textPaint)
    }
    val fm = textPaint.fontMetrics
    val firstBaseline = height - bandHeight + padding - fm.ascent
    val secondBaseline = firstBaseline + lineHeight
    val thirdBaseline = secondBaseline + lineHeight
    drawRow(row1, firstBaseline)
    drawRow(row2, secondBaseline)
    drawRow(row3, thirdBaseline)
    textPaint.clearShadowLayer()
    return result
}

/**
 * Object wrapper for Java interop / discoverability.
 * Delegates to top-level functions to keep single implementation.
 */
object WatermarkUtils {
    @JvmStatic
    fun addPresensiWatermark(
        bitmap: Bitmap,
        studentName: String,
        studentClass: String,
        location: Location?,
        timestamp: LocalDateTime,
    ): Bitmap = com.gynda.fridaystm.util.addPresensiWatermark(
        bitmap, studentName, studentClass, location, timestamp
    )

    @JvmStatic
    fun addLarkamWatermark(
        bitmap: Bitmap,
        studentName: String,
        studentClass: String,
        location: Location?,
        timestamp: LocalDateTime,
        totalDistanceKm: Float,
        durationFormatted: String,
    ): Bitmap = com.gynda.fridaystm.util.addLarkamWatermark(
        bitmap, studentName, studentClass, location, timestamp, totalDistanceKm, durationFormatted
    )

    @JvmStatic
    fun toCompressJpegByteArray(bitmap: Bitmap, quality: Int = 80): ByteArray =
        bitmap.toCompressJpegByteArray(quality)
}
