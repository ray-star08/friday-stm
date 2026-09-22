package com.gynda.fridaystm.viewmodel

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Rect
import android.media.Image
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import com.gynda.fridaystm.data.model.User
import com.gynda.fridaystm.data.repository.PresensiRepository
import com.gynda.fridaystm.data.repository.StorageRepository
import com.gynda.fridaystm.util.LocationFix
import com.gynda.fridaystm.util.SCHOOL_LATITUDE
import com.gynda.fridaystm.util.SCHOOL_LONGITUDE
import com.gynda.fridaystm.util.TimeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PresensiCameraViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    companion object {
        private val fixedTime: LocalDateTime = LocalDateTime.of(2026, 9, 6, 8, 20, 57)
    }

    private inner class FakeTime : TimeProvider {
        override fun now(): LocalDateTime = fixedTime
        override fun weekOfYear(): Int = 36
    }

    private class FakeStorageRepository(
        var shouldFail: Boolean = false,
        var lastUserId: String? = null,
        var lastBitmap: Bitmap? = null,
    ) : StorageRepository {
        var uploadCalls = 0
        override suspend fun uploadPresensiSelfie(
            userId: String,
            bitmap: Bitmap,
            timestamp: LocalDateTime,
        ): Result<String> {
            uploadCalls++
            lastUserId = userId
            lastBitmap = bitmap
            return if (shouldFail) Result.failure(IllegalStateException("upload failed"))
            else Result.success("https://storage.example.com/presensi_selfies/$userId/${timestamp}.jpg")
        }

        override suspend fun uploadPresensiBytes(
            userId: String,
            bytes: ByteArray,
            storageFileName: String,
        ): Result<String> = Result.failure(IllegalStateException("bytes path unused in this test"))
    }

    private class FakePresensiRepository(
        var shouldFail: Boolean = false,
    ) : PresensiRepository {
        var saveCalls = 0
        var lastUserId: String? = null
        var lastImageUrl: String? = null
        override suspend fun savePresensi(
            userId: String,
            timestamp: LocalDateTime,
            imageUrl: String,
            lat: Double?,
            lng: Double?,
            studentName: String,
            studentClass: String,
        ): Result<Unit> {
            saveCalls++
            lastUserId = userId
            lastImageUrl = imageUrl
            return if (shouldFail) Result.failure(IllegalStateException("firestore failed"))
            else Result.success(Unit)
        }

        override fun getPresensiHistory(userId: String): kotlinx.coroutines.flow.Flow<Result<List<com.gynda.fridaystm.data.model.PresensiRecord>>> =
            kotlinx.coroutines.flow.flowOf(Result.success(emptyList()))
    }

    private val fakeUser = User(
        uid = "user123",
        nis = "2024001",
        nama = "Gynda Rayhan J.P.",
        grade = 11,
        kelas = "XI RPL A",
        role = "student"
    )

    private fun dummyBitmap(w: Int = 800, h: Int = 600): Bitmap {
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
    }

    private class FakeImageProxy(
        private val w: Int = 800,
        private val h: Int = 600,
        var closed: Boolean = false,
    ) : ImageProxy {
        override fun close() { closed = true }
        override fun getWidth(): Int = w
        override fun getHeight(): Int = h
        override fun getFormat(): Int = ImageFormat.JPEG
        override fun getPlanes(): Array<ImageProxy.PlaneProxy> {
            val bmp = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
            val stream = java.io.ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 80, stream)
            val bytes = stream.toByteArray()
            val buffer = ByteBuffer.wrap(bytes)
            val plane = object : ImageProxy.PlaneProxy {
                override fun getRowStride(): Int = w
                override fun getPixelStride(): Int = 1
                override fun getBuffer(): ByteBuffer = buffer
            }
            return arrayOf(plane)
        }
        override fun getImageInfo(): ImageInfo = object : ImageInfo {
            override fun getRotationDegrees(): Int = 0
            override fun getTimestamp(): Long = 0L
            override fun getTagBundle(): androidx.camera.core.impl.TagBundle =
                androidx.camera.core.impl.TagBundle.emptyBundle()
            override fun populateExifData(builder: androidx.camera.core.impl.utils.ExifData.Builder) {}
            override fun getSensorToBufferTransformMatrix(): android.graphics.Matrix = android.graphics.Matrix()
        }
        override fun getImage(): Image? = null
        override fun getCropRect(): Rect = Rect(0, 0, w, h)
        override fun setCropRect(rect: Rect?) {}
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `success flow via processBitmap watermark upload save to Success`() = runTest {
        val storage = FakeStorageRepository()
        val presensi = FakePresensiRepository()
        val locationProvider = FakeLocationProvider(LocationFix(lat = SCHOOL_LATITUDE, lng = SCHOOL_LONGITUDE, isMock = false))
        val auth = FakeAuthRepository(profiles = mapOf(fakeUser.uid to fakeUser), initialUid = fakeUser.uid)
        val vm = PresensiCameraViewModel(
            storageRepository = storage,
            presensiRepository = presensi,
            locationProvider = locationProvider,
            authRepository = auth,
            timeProvider = FakeTime()
        )

        vm.processBitmap(dummyBitmap())

        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is PresensiCameraUiState.Success)
        val url = (state as PresensiCameraUiState.Success).downloadUrl
        assertTrue(url.contains("presensi_selfies/${fakeUser.uid}"))
        assertEquals(1, storage.uploadCalls)
        assertEquals(fakeUser.uid, storage.lastUserId)
        assertTrue(storage.lastBitmap != null)
        assertEquals(1, presensi.saveCalls)
        assertEquals(fakeUser.uid, presensi.lastUserId)
        assertEquals(url, presensi.lastImageUrl)
    }

    @Test
    fun `success flow via ImageProxy closes proxy and succeeds`() = runTest {
        val storage = FakeStorageRepository()
        val presensi = FakePresensiRepository()
        val locationProvider = FakeLocationProvider(LocationFix(lat = SCHOOL_LATITUDE, lng = SCHOOL_LONGITUDE, isMock = false))
        val auth = FakeAuthRepository(profiles = mapOf(fakeUser.uid to fakeUser), initialUid = fakeUser.uid)

        val dummy = dummyBitmap()
        val vm = PresensiCameraViewModel(
            storageRepository = storage,
            presensiRepository = presensi,
            locationProvider = locationProvider,
            authRepository = auth,
            timeProvider = FakeTime(),
            imageProxyConverter = { _ -> dummy }
        )

        val fakeProxy = FakeImageProxy()
        vm.onImageCaptured(fakeProxy)

        advanceUntilIdle()

        assertTrue(fakeProxy.closed)
        val state = vm.uiState.value
        assertTrue(state is PresensiCameraUiState.Success)
        assertEquals(1, storage.uploadCalls)
        assertEquals(1, presensi.saveCalls)
    }

    @Test
    fun `failure when location null shows Error`() = runTest {
        val storage = FakeStorageRepository()
        val presensi = FakePresensiRepository()
        val locationProvider = FakeLocationProvider()
        locationProvider.fail()
        val auth = FakeAuthRepository(profiles = mapOf(fakeUser.uid to fakeUser), initialUid = fakeUser.uid)
        val vm = PresensiCameraViewModel(
            storageRepository = storage,
            presensiRepository = presensi,
            locationProvider = locationProvider,
            authRepository = auth,
            timeProvider = FakeTime()
        )

        vm.processBitmap(dummyBitmap())
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is PresensiCameraUiState.Error)
        assertTrue((state as PresensiCameraUiState.Error).message.contains("Lokasi", ignoreCase = true))
        assertEquals(0, storage.uploadCalls)
        assertEquals(0, presensi.saveCalls)
    }

    @Test
    fun `failure when upload fails shows Error`() = runTest {
        val storage = FakeStorageRepository(shouldFail = true)
        val presensi = FakePresensiRepository()
        val locationProvider = FakeLocationProvider(LocationFix(lat = SCHOOL_LATITUDE, lng = SCHOOL_LONGITUDE, isMock = false))
        val auth = FakeAuthRepository(profiles = mapOf(fakeUser.uid to fakeUser), initialUid = fakeUser.uid)
        val vm = PresensiCameraViewModel(
            storageRepository = storage,
            presensiRepository = presensi,
            locationProvider = locationProvider,
            authRepository = auth,
            timeProvider = FakeTime()
        )

        vm.processBitmap(dummyBitmap())
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is PresensiCameraUiState.Error)
        assertTrue((state as PresensiCameraUiState.Error).message.isNotBlank())
        assertEquals(1, storage.uploadCalls)
        assertEquals(0, presensi.saveCalls)
    }

    @Test
    fun `failure when firestore save fails shows Error`() = runTest {
        val storage = FakeStorageRepository()
        val presensi = FakePresensiRepository(shouldFail = true)
        val locationProvider = FakeLocationProvider(LocationFix(lat = SCHOOL_LATITUDE, lng = SCHOOL_LONGITUDE, isMock = false))
        val auth = FakeAuthRepository(profiles = mapOf(fakeUser.uid to fakeUser), initialUid = fakeUser.uid)
        val vm = PresensiCameraViewModel(
            storageRepository = storage,
            presensiRepository = presensi,
            locationProvider = locationProvider,
            authRepository = auth,
            timeProvider = FakeTime()
        )

        vm.processBitmap(dummyBitmap())
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is PresensiCameraUiState.Error)
        assertEquals(1, storage.uploadCalls)
        assertEquals(1, presensi.saveCalls)
    }

    @Test
    fun `ImageProxy is closed even on upload failure`() = runTest {
        val storage = FakeStorageRepository(shouldFail = true)
        val presensi = FakePresensiRepository()
        val locationProvider = FakeLocationProvider(LocationFix(lat = SCHOOL_LATITUDE, lng = SCHOOL_LONGITUDE, isMock = false))
        val auth = FakeAuthRepository(profiles = mapOf(fakeUser.uid to fakeUser), initialUid = fakeUser.uid)
        val dummy = dummyBitmap()
        val vm = PresensiCameraViewModel(
            storageRepository = storage,
            presensiRepository = presensi,
            locationProvider = locationProvider,
            authRepository = auth,
            timeProvider = FakeTime(),
            imageProxyConverter = { _ -> dummy }
        )
        val fakeProxy = FakeImageProxy()
        vm.onImageCaptured(fakeProxy)
        advanceUntilIdle()
        assertTrue(fakeProxy.closed)
        assertTrue(vm.uiState.value is PresensiCameraUiState.Error)
    }

    @Test
    fun `watermark applied before upload bitmap is watermarked size valid`() = runTest {
        val storage = FakeStorageRepository()
        val presensi = FakePresensiRepository()
        val locationProvider = FakeLocationProvider(LocationFix(lat = SCHOOL_LATITUDE, lng = SCHOOL_LONGITUDE, isMock = false))
        val auth = FakeAuthRepository(profiles = mapOf(fakeUser.uid to fakeUser), initialUid = fakeUser.uid)
        val vm = PresensiCameraViewModel(
            storageRepository = storage,
            presensiRepository = presensi,
            locationProvider = locationProvider,
            authRepository = auth,
            timeProvider = FakeTime()
        )

        val large = dummyBitmap(2000, 1500)
        vm.processBitmap(large)
        advanceUntilIdle()

        val uploadedBmp = storage.lastBitmap
        assertTrue(uploadedBmp != null)
        assertEquals(1280, uploadedBmp!!.width)
        assertTrue(vm.uiState.value is PresensiCameraUiState.Success)
    }

    @Test
    fun `processBitmap outsideRadius returnsErrorAndCancelsUpload`() = runTest {
        val storage = FakeStorageRepository()
        val presensi = FakePresensiRepository()
        // Far outside school radius (>100m)
        val locationProvider = FakeLocationProvider(LocationFix(lat = -6.9000, lng = 107.6000, isMock = false))
        val auth = FakeAuthRepository(profiles = mapOf(fakeUser.uid to fakeUser), initialUid = fakeUser.uid)
        val vm = PresensiCameraViewModel(
            storageRepository = storage,
            presensiRepository = presensi,
            locationProvider = locationProvider,
            authRepository = auth,
            timeProvider = FakeTime()
        )

        vm.processBitmap(dummyBitmap())
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is PresensiCameraUiState.Error)
        val msg = (state as PresensiCameraUiState.Error).message
        assertTrue(msg.contains("Di luar area sekolah", ignoreCase = true))
        assertTrue(msg.contains("meter", ignoreCase = true))
        assertEquals(0, storage.uploadCalls)
        assertEquals(0, presensi.saveCalls)
    }
}
