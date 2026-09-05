package com.gynda.fridaystm.ui.component

import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gynda.fridaystm.R
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Live camera preview, front-facing by default (selfie evidence, task 4.3).
 *
 * The one sanctioned `AndroidView` wrapper (SKILL.md §1): CameraX's `PreviewView`
 * has no Compose equivalent, so it is hosted here and never leaked upward. The
 * capture handle is surfaced through [onCaptureReady] as a plain suspend lambda —
 * callers get a `Bitmap` back without ever touching `ImageCapture` directly.
 *
 * @param onCaptureReady invoked once the camera is bound; provides a `suspend`
 *   capture function the screen calls when the user taps the shutter.
 */
@Composable
fun CameraPreview(
    onCaptureReady: (capture: suspend () -> Result<Bitmap>) -> Unit,
    modifier: Modifier = Modifier,
    lensFacing: Int = CameraSelector.LENS_FACING_FRONT,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().build() }
    val previewDescription = stringResource(R.string.cd_camera_preview)

    AndroidView(
        modifier = modifier.semantics { contentDescription = previewDescription },
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val selector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
                onCaptureReady { imageCapture.captureBitmap(ctx) }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
    )
}

/**
 * Takes one photo and decodes it to a [Bitmap], suspending until the capture
 * completes. Wraps CameraX's callback in a coroutine (SKILL.md §5 — no raw
 * listeners leaking into app code). Front-camera frames are mirror-corrected by
 * CameraX's default output; the bitmap is returned upright.
 */
private suspend fun ImageCapture.captureBitmap(
    context: android.content.Context,
): Result<Bitmap> = suspendCancellableCoroutine { cont ->
    takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap = runCatching { image.toBitmap() }
                image.close()
                cont.resume(bitmap)
            }

            override fun onError(exception: ImageCaptureException) {
                cont.resumeWithException(exception)
            }
        },
    )
}
