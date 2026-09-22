package com.gynda.fridaystm.viewmodel

import com.gynda.fridaystm.util.CapturedPhoto
import com.gynda.fridaystm.util.CameraCapturedPhoto
import com.gynda.fridaystm.util.BitmapCapturedPhoto
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Rect
import android.media.Image
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import com.gynda.fridaystm.data.model.CaptureDraft
import com.gynda.fridaystm.data.model.User
import com.gynda.fridaystm.data.repository.CaptureSubmissionRepository
import com.gynda.fridaystm.data.repository.PresensiSubmitResult
import com.gynda.fridaystm.util.LocationFix
import com.gynda.fridaystm.util.SCHOOL_LATITUDE
import com.gynda.fridaystm.util.SCHOOL_LONGITUDE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.nio.ByteBuffer
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PresensiCameraViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val user = User(uid = "student-a", nama = "Synthetic Student", kelas = "XI Test", grade = 11, role = "student")
    private val time = FakeTimeProvider(LocalDateTime.of(2026, 8, 14, 7, 0))
    private val auth = FakeAuthRepository(mapOf(user.uid to user), user.uid)
    private val location = FakeLocationProvider(LocationFix(SCHOOL_LATITUDE, SCHOOL_LONGITUDE, false))
    private val repo = FakeCaptureRepository()
    private fun vm() = PresensiCameraViewModel(repo, location, auth, time, processingDispatcher = dispatcher)
    private fun bitmap() = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun `camera hardware failure is visible and retryable without a fake capture`() = runTest {
        val vm = vm()
        vm.onCameraError("Kamera tidak tersedia")
        assertEquals(PresensiCameraUiState.Error("Kamera tidak tersedia"), vm.uiState.value)
        assertTrue(repo.drafts.isEmpty())
        vm.reset()
        assertEquals(PresensiCameraUiState.Idle, vm.uiState.value)
    }

    @Test fun `JPEG sensor rotation is applied before watermarking`() {
        val original = bitmap()
        val bytes = java.io.ByteArrayOutputStream().use { output ->
            assertTrue(original.compress(Bitmap.CompressFormat.JPEG, 90, output))
            output.toByteArray()
        }
        original.recycle()
        val proxy = FakeCaptureProxy(bytes, 90)
        val rotated = com.gynda.fridaystm.util.decodeCaptureBitmap(proxy)
        assertEquals(600, rotated.width)
        assertEquals(800, rotated.height)
        rotated.recycle()
        assertEquals(0, proxy.closes)
    }

    @Test fun `callback arriving after viewmodel cleared still closes photo`() = runTest {
        val proxy = FakeCaptureProxy()
        val vm = vm()
        val store = androidx.lifecycle.ViewModelStore()
        store.put("camera", vm)
        store.clear()
        vm.onPhotoCaptured(CameraCapturedPhoto(proxy))
        advanceUntilIdle()
        assertEquals(1, proxy.closes)
        assertTrue(repo.drafts.isEmpty())
    }

    @Test fun `staff role and account change during preparation never submit evidence`() = runTest {
        val staff = user.copy(role = "instructor")
        val staffAuth = FakeAuthRepository(mapOf(staff.uid to staff), staff.uid)
        val staffVm = PresensiCameraViewModel(repo, location, staffAuth, time, processingDispatcher = dispatcher)
        staffVm.onPhotoCaptured(BitmapCapturedPhoto(bitmap()))
        advanceUntilIdle()
        assertTrue(staffVm.uiState.value is PresensiCameraUiState.Error)
        assertTrue(repo.drafts.isEmpty())
        val proxy = FakeCaptureProxy()
        val switching = PresensiCameraViewModel(repo, location, auth, time, processingDispatcher = dispatcher)
        switching.onPhotoCaptured(object : CapturedPhoto {
            override suspend fun jpeg(draft: CaptureDraft): ByteArray { auth.emitAuthState("other"); return byteArrayOf(1) }
            override fun close() = proxy.close()
        })
        advanceUntilIdle()
        assertTrue(switching.uiState.value is PresensiCameraUiState.Error)
        assertTrue(repo.drafts.isEmpty())
        assertEquals(1, proxy.closes)
    }

    @Test fun `location failure and invalid coordinates never submit evidence`() = runTest {
        for (fix in listOf(LocationFix(Double.NaN, SCHOOL_LONGITUDE, false), LocationFix(91.0, 107.5, false), LocationFix(0.0, 0.0, false))) {
            location.set(fix)
            val vm = vm()
            vm.onPhotoCaptured(BitmapCapturedPhoto(bitmap()))
            advanceUntilIdle()
            assertTrue(vm.uiState.value is PresensiCameraUiState.Error)
        }
        location.fail()
        val vm = vm()
        vm.onPhotoCaptured(BitmapCapturedPhoto(bitmap()))
        advanceUntilIdle()
        assertTrue(vm.uiState.value is PresensiCameraUiState.Error)
        assertTrue(repo.drafts.isEmpty())
    }

    @Test fun `cleared viewmodel closes proxy without turning cancellation into an error`() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val proxy = FakeCaptureProxy()
        val vm = PresensiCameraViewModel(repo, location, auth, time, processingDispatcher = dispatcher)
        val store = androidx.lifecycle.ViewModelStore()
        store.put("camera", vm)
        vm.onPhotoCaptured(object : CapturedPhoto {
            override suspend fun jpeg(draft: CaptureDraft): ByteArray { gate.await(); return byteArrayOf(1) }
            override fun close() = proxy.close()
        })
        testScheduler.runCurrent()
        store.clear()
        advanceUntilIdle()
        assertEquals(1, proxy.closes)
        assertFalse(vm.uiState.value is PresensiCameraUiState.Error)
        assertTrue(repo.drafts.isEmpty())
    }

    @Test fun `Larkam capture keeps explicit stats and cannot cross owners or missing intent`() = runTest {
        val run = com.gynda.fridaystm.data.model.LarkamCapture(1.2, 400, listOf(mapOf("lat" to SCHOOL_LATITUDE, "lng" to SCHOOL_LONGITUDE)))
        val intent = com.gynda.fridaystm.util.LarkamCaptureIntent("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee", user.uid, time.today(), run)
        val vm = PresensiCameraViewModel(repo, location, auth, time, processingDispatcher = dispatcher, larkamIntent = intent, requiresLarkamIntent = true)
        vm.onPhotoCaptured(BitmapCapturedPhoto(bitmap()))
        advanceUntilIdle()
        assertEquals(run, repo.drafts.single().larkam)
        assertNotEquals("run navigation ID is not the photo ID", intent.captureId, repo.drafts.single().captureId)
        for (bad in listOf(null, intent.copy(ownerUid = "other"), intent.copy(date = time.today().minusDays(7)))) {
            val before = repo.drafts.size
            val invalid = PresensiCameraViewModel(repo, location, auth, time, processingDispatcher = dispatcher, larkamIntent = bad, requiresLarkamIntent = true)
            invalid.onPhotoCaptured(BitmapCapturedPhoto(bitmap()))
            advanceUntilIdle()
            assertTrue(invalid.uiState.value is PresensiCameraUiState.Error)
            assertEquals(before, repo.drafts.size)
        }
    }

    @Test fun `retaking a failed Larkam photo gets a fresh identity and preserves finished run`() = runTest {
        val run = com.gynda.fridaystm.data.model.LarkamCapture(1.2, 400)
        val intent = com.gynda.fridaystm.util.LarkamCaptureIntent("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee", user.uid, time.today(), run)
        val retained = mutableMapOf<String, CaptureDraft>()
        var attempts = 0
        val captureRepo = object : CaptureSubmissionRepository {
            override suspend fun submitCapture(draft: CaptureDraft, imageBytes: ByteArray): Result<PresensiSubmitResult> {
                attempts++
                val old = retained[draft.captureId]
                return when {
                    old != null && old != draft -> Result.failure(IllegalStateException("Capture ID memiliki bukti berbeda"))
                    attempts == 1 -> {
                        retained[draft.captureId] = draft
                        Result.failure(IllegalStateException("permission denied; retained evidence"))
                    }
                    else -> {
                        retained[draft.captureId] = draft
                        Result.success(PresensiSubmitResult.QueuedOffline)
                    }
                }
            }
        }
        val vm = PresensiCameraViewModel(captureRepo, location, auth, time, processingDispatcher = dispatcher, larkamIntent = intent, requiresLarkamIntent = true)
        vm.onPhotoCaptured(BitmapCapturedPhoto(bitmap()))
        advanceUntilIdle()
        assertTrue(vm.uiState.value is PresensiCameraUiState.Error)
        val first = retained.values.single()
        vm.reset()
        time.set(time.now().plusSeconds(10))
        vm.onPhotoCaptured(BitmapCapturedPhoto(bitmap()))
        advanceUntilIdle()
        assertEquals(PresensiCameraUiState.QueuedOffline, vm.uiState.value)
        assertEquals(2, retained.size)
        val second = retained.values.last()
        assertNotEquals(first.captureId, second.captureId)
        assertNotEquals(intent.captureId, second.captureId)
        assertEquals(first, retained[first.captureId])
        assertEquals(run, first.larkam)
        assertEquals(run, second.larkam)
    }

    @Test fun `outside Friday capture window is rejected before persistence`() = runTest {
        time.set(LocalDateTime.of(2026, 8, 15, 7, 0))
        val vm = vm()
        vm.onPhotoCaptured(BitmapCapturedPhoto(bitmap()))
        advanceUntilIdle()
        assertTrue(vm.uiState.value is PresensiCameraUiState.Error)
        assertTrue(repo.drafts.isEmpty())
    }

    @Test fun `two capture callbacks submit once and close rejected proxy`() = runTest {
        val vm = vm()
        val proxy = FakeCaptureProxy()
        vm.onPhotoCaptured(BitmapCapturedPhoto(bitmap()))
        vm.onPhotoCaptured(CameraCapturedPhoto(proxy))
        advanceUntilIdle()
        assertEquals(1, repo.drafts.size)
        assertEquals(1, proxy.closes)
        assertTrue(vm.uiState.value is PresensiCameraUiState.Success)
    }

    @Test fun `typed submission owns the complete save not upload-only`() = runTest {
        repo.result = Result.success(PresensiSubmitResult.QueuedOffline)
        val vm = vm()
        vm.onPhotoCaptured(BitmapCapturedPhoto(bitmap()))
        advanceUntilIdle()
        assertEquals(1, repo.drafts.size)
        assertEquals(user.uid, repo.drafts.single().userId)
        assertNull(repo.drafts.single().larkam)
        assertTrue(repo.images.single().isNotEmpty())
        assertEquals(PresensiCameraUiState.QueuedOffline, vm.uiState.value)
    }

    @Test fun `mock location is rejected before any upload`() = runTest {
        location.set(LocationFix(SCHOOL_LATITUDE, SCHOOL_LONGITUDE, true))
        val vm = vm()
        vm.onPhotoCaptured(BitmapCapturedPhoto(bitmap()))
        advanceUntilIdle()
        assertTrue(vm.uiState.value is PresensiCameraUiState.Error)
        assertTrue(repo.drafts.isEmpty())
    }

    @Test fun `corrupt JPEG must not become fabricated evidence`() = runTest {
        val vm = vm()
        val proxy = FakeCaptureProxy(byteArrayOf(1, 2, 3))
        vm.onPhotoCaptured(CameraCapturedPhoto(proxy))
        advanceUntilIdle()
        assertTrue(vm.uiState.value is PresensiCameraUiState.Error)
        assertTrue(repo.drafts.isEmpty())
        assertEquals(1, proxy.closes)
    }

    @Test fun `repository failure cannot display success`() = runTest {
        repo.result = Result.failure(IllegalStateException("commit failed"))
        val vm = vm()
        vm.onPhotoCaptured(BitmapCapturedPhoto(bitmap()))
        advanceUntilIdle()
        assertEquals(PresensiCameraUiState.Error("commit failed"), vm.uiState.value)
    }

    @Test fun `confirmed remote write displays success`() = runTest {
        val vm = vm()
        vm.onPhotoCaptured(BitmapCapturedPhoto(bitmap()))
        advanceUntilIdle()
        assertEquals(PresensiCameraUiState.Success("https://example.test/photo.jpg"), vm.uiState.value)
    }
}

private class FakeCaptureRepository : CaptureSubmissionRepository {
    var result: Result<PresensiSubmitResult> = Result.success(PresensiSubmitResult.Uploaded("https://example.test/photo.jpg"))
    val drafts = mutableListOf<CaptureDraft>()
    val images = mutableListOf<ByteArray>()
    override suspend fun submitCapture(draft: CaptureDraft, imageBytes: ByteArray): Result<PresensiSubmitResult> {
        drafts += draft
        images += imageBytes
        return result
    }
}

internal class FakeCaptureProxy(private val bytes: ByteArray = byteArrayOf(1, 2, 3), private val rotation: Int = 0) : ImageProxy {
    var closes = 0
    override fun close() { closes++ }
    override fun getWidth() = 800
    override fun getHeight() = 600
    override fun getFormat() = ImageFormat.JPEG
    override fun getPlanes() = arrayOf(object : ImageProxy.PlaneProxy {
        override fun getRowStride() = 800
        override fun getPixelStride() = 1
        override fun getBuffer() = ByteBuffer.wrap(bytes)
    })
    override fun getImageInfo(): ImageInfo = object : ImageInfo {
        override fun getRotationDegrees() = rotation
        override fun getTimestamp() = 0L
        override fun getTagBundle() = androidx.camera.core.impl.TagBundle.emptyBundle()
        override fun populateExifData(builder: androidx.camera.core.impl.utils.ExifData.Builder) {}
        override fun getSensorToBufferTransformMatrix() = android.graphics.Matrix()
    }
    override fun getImage(): Image? = null
    override fun getCropRect() = Rect(0, 0, width, height)
    override fun setCropRect(rect: Rect?) {}
}
