package com.gynda.fridaystm.util

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import com.gynda.fridaystm.data.model.CaptureDraft
import java.util.concurrent.atomic.AtomicBoolean

/** Exactly one owner closes CameraX even if a callback arrives after cancellation. */
class CameraCapturedPhoto(private val proxy: ImageProxy) : CapturedPhoto {
    private val closed = AtomicBoolean(false)
    override suspend fun jpeg(draft: CaptureDraft): ByteArray = processCaptureBitmap(decodeCaptureBitmap(proxy), draft)
    override fun close() { if (closed.compareAndSet(false, true)) proxy.close() }
}

/** Bitmap adapter also releases images rejected before processing begins. */
class BitmapCapturedPhoto(private val bitmap: Bitmap) : CapturedPhoto {
    override suspend fun jpeg(draft: CaptureDraft): ByteArray = processCaptureBitmap(bitmap, draft)
    override fun close() { if (!bitmap.isRecycled) bitmap.recycle() }
}
