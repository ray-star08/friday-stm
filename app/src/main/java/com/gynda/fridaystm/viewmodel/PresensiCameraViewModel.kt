package com.gynda.fridaystm.viewmodel

import com.gynda.fridaystm.util.CapturedPhoto
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gynda.fridaystm.data.model.CaptureDraft
import com.gynda.fridaystm.data.repository.AuthRepository
import com.gynda.fridaystm.data.repository.CaptureSubmissionRepository
import com.gynda.fridaystm.data.repository.FirebaseAuthRepository
import com.gynda.fridaystm.data.repository.PresensiSubmitResult
import com.gynda.fridaystm.util.LocationProvider
import com.gynda.fridaystm.util.SystemTimeProvider
import com.gynda.fridaystm.util.TimeProvider
import com.gynda.fridaystm.util.isWithinSchoolRadius
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** Supplementary photo evidence only. The repository owns both durable queueing and remote commit. */
class PresensiCameraViewModel(
    private val captureRepository: CaptureSubmissionRepository,
    private val locationProvider: LocationProvider,
    private val authRepository: AuthRepository,
    private val timeProvider: TimeProvider,
    private val processingDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default,
    private val larkamIntent: com.gynda.fridaystm.util.LarkamCaptureIntent? = null,
    private val requiresLarkamIntent: Boolean = false,
) : ViewModel() {
    private val _uiState = MutableStateFlow<PresensiCameraUiState>(PresensiCameraUiState.Idle)
    val uiState: StateFlow<PresensiCameraUiState> = _uiState.asStateFlow()

    fun onPhotoCaptured(photo: CapturedPhoto) {
        val close = photo::close
        if (_uiState.value == PresensiCameraUiState.Loading || _uiState.value is PresensiCameraUiState.Success || _uiState.value == PresensiCameraUiState.QueuedOffline) {
            close()
            return
        }
        _uiState.value = PresensiCameraUiState.Loading
        viewModelScope.launch {
            try {
                val uid = authRepository.currentUid ?: error("Sesi berakhir. Masuk kembali.")
                val user = authRepository.getUserProfile(uid).getOrThrow()
                check(user.uid == uid && user.role in setOf("student", "class_rep")) { "Akun ini tidak dapat mengirim foto siswa" }
                check(authRepository.currentUid == uid) { "Akun berubah. Ulangi pengambilan foto." }
                val captureTime = timeProvider.now()
                check(com.gynda.fridaystm.domain.resolvePhase(captureTime) in setOf(
                    com.gynda.fridaystm.domain.FridayPhase.PEMBIASAAN,
                    com.gynda.fridaystm.domain.FridayPhase.CHECKOUT,
                )) { "Foto kegiatan hanya tersedia pada Jumat 06:30–08:30 WIB" }
                val fix = locationProvider.currentLocation().getOrThrow()
                check(!fix.isMock) { "Lokasi palsu terdeteksi" }
                check(isWithinSchoolRadius(fix.lat, fix.lng)) { "Di luar area sekolah" }
                val intent = if (requiresLarkamIntent) {
                    checkNotNull(larkamIntent) { "Sesi Larkam hilang. Kembali ke pelacak dan mulai ulang." }.also {
                        check(it.ownerUid == uid && it.date == captureTime.toLocalDate()) { "Sesi Larkam tidak sesuai akun/tanggal" }
                    }
                } else null
                val draft = CaptureDraft(
                    captureId = UUID.randomUUID().toString(), userId = uid,
                    timestamp = captureTime, lat = fix.lat, lng = fix.lng,
                    studentName = user.nama.ifBlank { "Siswa" }, studentClass = user.kelas.ifBlank { "-" },
                    larkam = intent?.run,
                )
                val bytes = withContext(processingDispatcher) { photo.jpeg(draft) }
                check(authRepository.currentUid == uid) { "Akun berubah. Ulangi pengambilan foto." }
                _uiState.value = when (val result = captureRepository.submitCapture(draft, bytes).getOrThrow()) {
                    is PresensiSubmitResult.Uploaded -> PresensiCameraUiState.Success(result.imageUrl)
                    PresensiSubmitResult.QueuedOffline -> PresensiCameraUiState.QueuedOffline
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _uiState.value = PresensiCameraUiState.Error(e.message ?: "Gagal memproses foto")
            }
        }.invokeOnCompletion { close() }
    }

    fun onCameraError(message: String) {
        if (_uiState.value == PresensiCameraUiState.Idle || _uiState.value is PresensiCameraUiState.Error) {
            _uiState.value = PresensiCameraUiState.Error(message)
        }
    }

    fun reset() { if (_uiState.value != PresensiCameraUiState.Loading) _uiState.value = PresensiCameraUiState.Idle }

    companion object {
        fun factory(
            captureRepository: CaptureSubmissionRepository,
            locationProvider: LocationProvider,
            authRepository: AuthRepository = FirebaseAuthRepository(),
            timeProvider: TimeProvider = SystemTimeProvider(),
            larkamIntent: com.gynda.fridaystm.util.LarkamCaptureIntent? = null,
            requiresLarkamIntent: Boolean = false,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { PresensiCameraViewModel(captureRepository, locationProvider, authRepository, timeProvider, larkamIntent = larkamIntent, requiresLarkamIntent = requiresLarkamIntent) }
        }
    }
}

sealed interface PresensiCameraUiState {
    data object Idle : PresensiCameraUiState
    data object Loading : PresensiCameraUiState
    data class Success(val downloadUrl: String) : PresensiCameraUiState
    data class Error(val message: String) : PresensiCameraUiState
    data object QueuedOffline : PresensiCameraUiState
}
