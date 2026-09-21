package com.gynda.fridaystm.util

import android.graphics.Bitmap
import android.graphics.Color
import android.location.Location
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [WatermarkUtils] / [addPresensiWatermark] and
 * [toCompressJpegByteArray].
 *
 * Uses Robolectric to provide Android [Bitmap] shadows on the JVM so tests can
 * run via `./gradlew test` without an emulator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WatermarkUtilsTest {

    private fun dummyBitmap(width: Int = 800, height: Int = 600, color: Int = Color.BLUE): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(color)
        return bmp
    }

    private fun dummyLocation(lat: Double = -6.8921, lng: Double = 107.5432): Location =
        Location("test").apply {
            latitude = lat
            longitude = lng
        }

    private val timestamp: LocalDateTime = LocalDateTime.of(2026, 9, 6, 8, 20, 57)

    @Test
    fun `watermark with dummy 800x600 and null location returns valid bitmap`() {
        val src = dummyBitmap(800, 600)
        val result = addPresensiWatermark(
            bitmap = src,
            studentName = "Gynda Rayhan J.P.",
            studentClass = "XI RPL A",
            location = null,
            timestamp = timestamp
        )

        assertNotNull(result)
        assertFalse(result.isRecycled)
        assertTrue(result.width > 0 && result.height > 0)
        // 800px should not be scaled
        assertEquals(800, result.width)
        assertEquals(600, result.height)
        // Config must be ARGB_8888 mutable
        assertEquals(Bitmap.Config.ARGB_8888, result.config)
        assertTrue(result.isMutable)
    }

    @Test
    fun `watermark with valid location does not throw and renders correctly`() {
        val src = dummyBitmap(800, 600, Color.RED)
        val loc = dummyLocation()

        val result = addPresensiWatermark(
            bitmap = src,
            studentName = "Budi Santoso",
            studentClass = "XII TKJ B",
            location = loc,
            timestamp = timestamp
        )

        assertNotNull(result)
        assertFalse(result.isRecycled)
        assertTrue(result.width > 0 && result.height > 0)
        // Verify not crashing on valid location path
        assertEquals(800, result.width)
    }

    @Test
    fun `watermark scales down when width exceeds 1280`() {
        val src = dummyBitmap(2000, 1500, Color.GREEN)
        val originalWidth = src.width

        val result = addPresensiWatermark(
            bitmap = src,
            studentName = "Siti",
            studentClass = "X RPL 1",
            location = dummyLocation(),
            timestamp = timestamp
        )

        assertNotNull(result)
        assertFalse(result.isRecycled)
        // Must be scaled to 1280
        assertEquals(1280, result.width)
        assertTrue(result.height < 1500)
        // Original should be recycled when scaled (OOM guard)
        assertTrue(src.isRecycled)
        // Aspect ratio preserved approx
        val expectedHeight = (1500 * (1280f / originalWidth)).toInt()
        assertEquals(expectedHeight, result.height)
    }

    @Test
    fun `watermark with 1280 width does not scale`() {
        val src = dummyBitmap(1280, 720)
        val result = addPresensiWatermark(
            bitmap = src,
            studentName = "Ahmad",
            studentClass = "XI RPL 2",
            location = null,
            timestamp = timestamp
        )
        assertEquals(1280, result.width)
        assertEquals(720, result.height)
    }

    @Test
    fun `toCompressJpegByteArray returns valid jpeg bytes`() {
        val src = dummyBitmap(800, 600)
        val watermarked = addPresensiWatermark(
            bitmap = src,
            studentName = "Gynda Rayhan J.P.",
            studentClass = "XI RPL A",
            location = dummyLocation(-6.8921, 107.5432),
            timestamp = timestamp
        )

        val bytes = watermarked.toCompressJpegByteArray(quality = 80)
        assertNotNull(bytes)
        assertTrue(bytes.isNotEmpty())
        // JPEG magic: FF D8 FF
        assertEquals(0xFF.toByte(), bytes[0])
        assertEquals(0xD8.toByte(), bytes[1])
        // Quality param is clamped 0..100 — test low/high still works
        val lowQuality = watermarked.toCompressJpegByteArray(quality = 10)
        val highQuality = watermarked.toCompressJpegByteArray(quality = 100)
        assertTrue(lowQuality.isNotEmpty())
        assertTrue(highQuality.isNotEmpty())
        // High quality should be larger than low quality for same image (generally)
        assertTrue(highQuality.size >= lowQuality.size)
    }

    @Test
    fun `watermark handles empty name class gracefully`() {
        val src = dummyBitmap(640, 480)
        val result = addPresensiWatermark(
            bitmap = src,
            studentName = "",
            studentClass = "",
            location = null,
            timestamp = LocalDateTime.now()
        )
        assertNotNull(result)
        assertFalse(result.isRecycled)
        assertTrue(result.width > 0)
    }

    @Test
    fun `object wrapper delegates correctly`() {
        val src = dummyBitmap(800, 600)
        val resultTopLevel = addPresensiWatermark(src, "Test", "X", null, timestamp)
        // Need fresh bitmap for second call since first recycles when copy
        val src2 = dummyBitmap(800, 600)
        val resultObj = WatermarkUtils.addPresensiWatermark(src2, "Test", "X", null, timestamp)
        assertNotNull(resultTopLevel)
        assertNotNull(resultObj)
        assertEquals(resultTopLevel.width, resultObj.width)
        assertEquals(resultTopLevel.height, resultObj.height)

        val bytes1 = resultTopLevel.toCompressJpegByteArray()
        val bytes2 = WatermarkUtils.toCompressJpegByteArray(resultObj, 80)
        assertTrue(bytes1.isNotEmpty())
        assertTrue(bytes2.isNotEmpty())
    }
}
