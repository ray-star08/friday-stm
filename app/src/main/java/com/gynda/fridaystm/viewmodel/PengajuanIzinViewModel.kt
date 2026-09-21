package com.gynda.fridaystm.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gynda.fridaystm.data.model.IzinRecord
import com.gynda.fridaystm.data.model.IzinType
import com.gynda.fridaystm.data.repository.AuthRepository
import com.gynda.fridaystm.data.repository.CloudinaryIzinProofUploader
import com.gynda.fridaystm.data.repository.FirebaseAuthRepository
import com.gynda.fridaystm.data.repository.FirebaseIzinRepository
import com.gynda.fridaystm.data.repository.IzinProofUploader
import com.gynda.fridaystm.data.repository.IzinRepository
import com.gynda.fridaystm.util.IzinStatus
import com.gynda.fridaystm.util.SystemTimeProvider
import com.gynda.fridaystm.util.TimeProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * Form state pengajuan izin/sakit — immutable, diobserve UI via [formState].
 *
 * Catatan testability (SKILL.md §3.2): [proofUri] disimpan sebagai [String]
 * (stringified `Uri`), bukan `android.net.Uri`, sehingga ViewModel murni JVM
 * dan bisa di-unit-test tanpa Robolectric. UI mengonversi `Uri <-> String`
 * di layer Composable; bytes gambar untuk upload disimpan terpisah via
 * [onProofSelected] dan tidak terekspos sebagai `Uri` framework.
 *
 * @property tipe SAKIT/IZIN, `null` = belum dipilih.
 * @property startDateMillis tanggal mulai (epoch-millis), `null` = belum dipilih.
 * @property endDateMillis tanggal selesai (epoch-millis), `null` = belum dipilih.
 * @property alasan deskripsi/alasan izin (min. [MIN_ALASAN_LENGTH] karakter).
 * @property proofUri stringified Uri bukti surat, `null` = belum dilampirkan.
 */
data class PengajuanIzinFormState(
    val tipe: IzinType? = null,
    val startDateMillis: Long? = null,
    val endDateMillis: Long? = null,
    val alasan: String = "",
    val proofUri: String? = null,
)

/**
 * Transient outcome pengajuan — dipisah dari [PengajuanIzinFormState] agar
 * status sekali-tampil (SUCCESS/ERROR) tidak mengotori field form (pola yang
 * sama dengan [SubmitStatus] di layar lain).
 */
sealed interface PengajuanIzinUiState {
    data object Idle : PengajuanIzinUiState
    data object Loading : PengajuanIzinUiState
    data object Success : PengajuanIzinUiState
    data class Error(val message: String) : PengajuanIzinUiState
}

/**
 * Drives `PengajuanIzinScreen`: memegang form, memvalidasi client-side, lalu
 * mengunggah bukti ke Storage (`permits/...`) dan menyimpan dokumen ke
 * `izin_records` dengan status PENDING.
 *
 * SKILL.md: tanpa tipe Android/Compose; waktu via [TimeProvider]; single
 * `StateFlow` per state; dependensi berupa interface agar testable.
 */
class PengajuanIzinViewModel(
    private val authRepository: AuthRepository,
    private val izinRepository: IzinRepository,
    private val proofUploader: IzinProofUploader,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val _formState = MutableStateFlow(PengajuanIzinFormState())
    val formState: StateFlow<PengajuanIzinFormState> = _formState.asStateFlow()

    private val _uiState = MutableStateFlow<PengajuanIzinUiState>(PengajuanIzinUiState.Idle)
    val uiState: StateFlow<PengajuanIzinUiState> = _uiState.asStateFlow()

    /** Bytes bukti (JPEG) untuk upload; disimpan di luar [formState] karena `ByteArray` tak cocok di `data class`. */
    private var proofBytes: ByteArray? = null

    fun onTipeChange(tipe: IzinType) {
        _formState.update { it.copy(tipe = tipe) }
        clearTransientError()
    }

    fun onStartDateChange(millis: Long?) {
        _formState.update { it.copy(startDateMillis = millis) }
        clearTransientError()
    }

    fun onEndDateChange(millis: Long?) {
        _formState.update { it.copy(endDateMillis = millis) }
        clearTransientError()
    }

    fun onAlasanChange(value: String) {
        _formState.update { it.copy(alasan = value) }
        clearTransientError()
    }

    /**
     * Dipanggil UI setelah picker mengembalikan Uri + bytes yang sudah dibaca
     * via `ContentResolver` (I/O tetap di UI layer; ViewModel hanya menyimpan).
     */
    fun onProofSelected(uri: String, bytes: ByteArray) {
        proofBytes = bytes
        _formState.update { it.copy(proofUri = uri) }
        clearTransientError()
    }

    fun onProofCleared() {
        proofBytes = null
        _formState.update { it.copy(proofUri = null) }
        clearTransientError()
    }

    /**
     * Validasi lalu submit: upload bukti → simpan dokumen `izin_records`.
     * Urutan validasi: tipe → tanggal → alasan → bukti.
     */
    fun onSubmit() {
        val form = _formState.value
        val violation = validate(form, proofBytes)
        if (violation != null) {
            _uiState.value = PengajuanIzinUiState.Error(violation)
            return
        }
        _uiState.value = PengajuanIzinUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                val uid = authRepository.currentUid
                    ?: throw IllegalStateException("Sesi berakhir. Silakan login ulang.")
                val user = authRepository.getUserProfile(uid).getOrElse { throw it }
                val bytes = proofBytes
                    ?: throw IllegalStateException(MSG_PROOF_REQUIRED)

                val proofUrl = proofUploader.uploadProof(uid, bytes).getOrElse { throw it }

                val record = IzinRecord(
                    userId = uid,
                    nama = user.nama,
                    kelas = user.kelas,
                    tipe = requireNotNull(form.tipe).wireValue,
                    alasan = form.alasan.trim(),
                    startDate = formatDate(requireNotNull(form.startDateMillis)),
                    endDate = formatDate(requireNotNull(form.endDateMillis)),
                    proofUrl = proofUrl,
                    status = IzinStatus.PENDING,
                )
                izinRepository.submitIzin(record).getOrElse { throw it }
                PengajuanIzinUiState.Success
            } catch (e: Exception) {
                PengajuanIzinUiState.Error(e.message ?: MSG_SUBMIT_FAILED)
            }
        }
    }

    /** Konsumsi status sekali-tampil agar tidak di-render ulang saat recomposition. */
    fun onUiStateConsumed() {
        _uiState.value = PengajuanIzinUiState.Idle
    }

    private fun clearTransientError() {
        if (_uiState.value is PengajuanIzinUiState.Error) {
            _uiState.value = PengajuanIzinUiState.Idle
        }
    }

    companion object {
        const val MIN_ALASAN_LENGTH = 10

        const val MSG_TIPE_REQUIRED = "Pilih tipe pengajuan (Sakit atau Izin)."
        const val MSG_DATE_REQUIRED = "Pilih tanggal mulai dan tanggal selesai."
        const val MSG_DATE_INVALID = "Tanggal mulai tidak boleh setelah tanggal selesai."
        const val MSG_ALASAN_REQUIRED = "Alasan minimal 10 karakter."
        const val MSG_PROOF_REQUIRED = "Bukti surat wajib dilampirkan."
        const val MSG_SUBMIT_FAILED = "Gagal mengirim pengajuan. Periksa koneksi lalu coba lagi."

        /**
         * Validasi murni form — `null` = valid, selain itu pesan error Indonesia.
         * `internal` agar bisa diuji langsung tanpa ViewModel; bytes dipisah
         * karena `ByteArray` tak idiomatis di dalam form `data class`.
         */
        internal fun validate(form: PengajuanIzinFormState, bytes: ByteArray?): String? {
            if (form.tipe == null) return MSG_TIPE_REQUIRED
            val start = form.startDateMillis
            val end = form.endDateMillis
            if (start == null || end == null) return MSG_DATE_REQUIRED
            if (start > end) return MSG_DATE_INVALID
            if (form.alasan.trim().length < MIN_ALASAN_LENGTH) return MSG_ALASAN_REQUIRED
            if (form.proofUri.isNullOrBlank() || bytes == null || bytes.isEmpty()) {
                return MSG_PROOF_REQUIRED
            }
            return null
        }

        internal fun formatDate(epochMillis: Long): String =
            Instant.ofEpochMilli(epochMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .toString()

        fun factory(
            authRepository: AuthRepository = FirebaseAuthRepository(),
            izinRepository: IzinRepository = FirebaseIzinRepository(),
            proofUploader: IzinProofUploader = CloudinaryIzinProofUploader(),
            timeProvider: TimeProvider = SystemTimeProvider(),
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                // `timeProvider` dipakai untuk stempel konsisten; nama file bukti
                // memakai jam uploader (lihat `FirebaseIzinProofUploader`).
                PengajuanIzinViewModel(authRepository, izinRepository, proofUploader, timeProvider)
            }
        }
    }
}
