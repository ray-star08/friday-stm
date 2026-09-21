package com.gynda.fridaystm.viewmodel

import com.gynda.fridaystm.data.model.IzinRecord
import com.gynda.fridaystm.data.model.IzinType
import com.gynda.fridaystm.data.model.User
import com.gynda.fridaystm.data.repository.IzinProofUploader
import com.gynda.fridaystm.data.repository.IzinRepository
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
import java.time.LocalDateTime

/**
 * Unit test [PengajuanIzinViewModel] di atas fake murni-JVM (SKILL.md §9):
 * tanpa Android framework, tanpa Firebase, tanpa Robolectric.
 *
 * ([FakeAuthRepository] dan [FakeTimeProvider] dipakai ulang dari
 * `TestDoubles.kt` di paket yang sama.)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PengajuanIzinViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val fakeUser = User(
        uid = "user123",
        nis = "2024001",
        nama = "Budi",
        grade = 11,
        kelas = "XI RPL 1",
        role = "student",
    )

    /** In-memory [IzinRepository]: menangkap dokumen yang disimpan. */
    private class FakeIzinRepository(
        var shouldFail: Boolean = false,
    ) : IzinRepository {
        var saveCalls = 0
        var lastRecord: IzinRecord? = null

        override suspend fun submitIzin(record: IzinRecord): Result<Unit> {
            saveCalls++
            lastRecord = record
            return if (shouldFail) Result.failure(IllegalStateException("firestore failed"))
            else Result.success(Unit)
        }

        override suspend fun updateIzinStatus(
            izinId: String,
            status: com.gynda.fridaystm.util.ApprovalStatus,
            note: String?,
            approverUid: String,
        ): Result<Unit> = Result.success(Unit)

        override fun observeIzinList(kelas: String, statusFilter: String): kotlinx.coroutines.flow.Flow<List<IzinRecord>> =
            kotlinx.coroutines.flow.flowOf(emptyList())
    }

    /** In-memory [IzinProofUploader]: menangkap bytes bukti yang diunggah. */
    private class FakeProofUploader(
        var shouldFail: Boolean = false,
    ) : IzinProofUploader {
        var uploadCalls = 0
        var lastUserId: String? = null
        var lastBytes: ByteArray? = null

        override suspend fun uploadProof(userId: String, bytes: ByteArray): Result<String> {
            uploadCalls++
            lastUserId = userId
            lastBytes = bytes
            return if (shouldFail) Result.failure(IllegalStateException("upload failed"))
            else Result.success("https://storage.example.com/permits/${userId}_123.jpg")
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        izinRepository: FakeIzinRepository = FakeIzinRepository(),
        proofUploader: FakeProofUploader = FakeProofUploader(),
    ): PengajuanIzinViewModel = PengajuanIzinViewModel(
        authRepository = FakeAuthRepository(
            profiles = mapOf(fakeUser.uid to fakeUser),
            initialUid = fakeUser.uid,
        ),
        izinRepository = izinRepository,
        proofUploader = proofUploader,
        timeProvider = FakeTimeProvider(LocalDateTime.of(2026, 9, 6, 8, 0)),
    )

    /** Mengisi form valid kecuali field yang diuji; tanggal 2026-09-07 s.d. 2026-09-08. */
    private fun PengajuanIzinViewModel.fillValidForm(
        alasan: String = "Demam tinggi dan disarankan dokter istirahat dua hari.",
        withProof: Boolean = true,
    ) {
        onTipeChange(IzinType.SAKIT)
        onStartDateChange(START_MILLIS)
        onEndDateChange(END_MILLIS)
        onAlasanChange(alasan)
        if (withProof) {
            onProofSelected("content://fake/proof.jpg", byteArrayOf(1, 2, 3))
        }
    }

    @Test
    fun submit_emptyAlasan_returnsValidationError() = runTest(testDispatcher) {
        val izinRepository = FakeIzinRepository()
        val proofUploader = FakeProofUploader()
        val vm = viewModel(izinRepository, proofUploader)
        vm.fillValidForm(alasan = "")

        vm.onSubmit()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is PengajuanIzinUiState.Error)
        assertEquals(
            PengajuanIzinViewModel.MSG_ALASAN_REQUIRED,
            (state as PengajuanIzinUiState.Error).message,
        )
        assertEquals(0, proofUploader.uploadCalls)
        assertEquals(0, izinRepository.saveCalls)
    }

    @Test
    fun submit_nullProofUri_returnsValidationError() = runTest(testDispatcher) {
        val izinRepository = FakeIzinRepository()
        val proofUploader = FakeProofUploader()
        val vm = viewModel(izinRepository, proofUploader)
        vm.fillValidForm(withProof = false)

        vm.onSubmit()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is PengajuanIzinUiState.Error)
        assertEquals(
            PengajuanIzinViewModel.MSG_PROOF_REQUIRED,
            (state as PengajuanIzinUiState.Error).message,
        )
        assertEquals(0, proofUploader.uploadCalls)
        assertEquals(0, izinRepository.saveCalls)
    }

    @Test
    fun submit_validData_uploadsProofAndSavesToFirestore() = runTest(testDispatcher) {
        val izinRepository = FakeIzinRepository()
        val proofUploader = FakeProofUploader()
        val vm = viewModel(izinRepository, proofUploader)
        vm.fillValidForm()

        vm.onSubmit()
        advanceUntilIdle()

        assertTrue(vm.uiState.value is PengajuanIzinUiState.Success)

        // Bukti diunggah dulu …
        assertEquals(1, proofUploader.uploadCalls)
        assertEquals(fakeUser.uid, proofUploader.lastUserId)
        assertTrue(proofUploader.lastBytes?.isNotEmpty() == true)

        // … lalu dokumen tersimpan dengan URL hasil upload.
        assertEquals(1, izinRepository.saveCalls)
        val record = izinRepository.lastRecord
        assertTrue(record != null)
        record!!
        assertEquals(fakeUser.uid, record.userId)
        assertEquals(fakeUser.nama, record.nama)
        assertEquals(fakeUser.kelas, record.kelas)
        assertEquals(IzinType.SAKIT.wireValue, record.tipe)
        assertEquals("https://storage.example.com/permits/${fakeUser.uid}_123.jpg", record.proofUrl)
        assertEquals("PENDING", record.status)
        assertTrue(record.alasan.length >= PengajuanIzinViewModel.MIN_ALASAN_LENGTH)
        assertTrue(record.startDate.isNotBlank())
        assertTrue(record.endDate.isNotBlank())
    }

    companion object {
        // 2026-09-07 dan 2026-09-08 00:00 UTC — start <= end, deterministik.
        private const val START_MILLIS = 1_787_270_400_000L
        private const val END_MILLIS = 1_787_356_800_000L
    }
}
