package com.gynda.fridaystm.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gynda.fridaystm.data.model.IzinRecord
import com.gynda.fridaystm.data.repository.AuthRepository
import com.gynda.fridaystm.data.repository.FirebaseAuthRepository
import com.gynda.fridaystm.data.repository.FirebaseIzinRepository
import com.gynda.fridaystm.data.repository.IzinRepository
import com.gynda.fridaystm.util.ApprovalFilter
import com.gynda.fridaystm.util.ApprovalStatus
import com.gynda.fridaystm.util.IzinStatus
import com.gynda.fridaystm.util.TeacherDashboardDefaults
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State untuk layar persetujuan izin wali kelas.
 *
 * @property selectedFilter filter tab saat ini (PENDING/APPROVED/REJECTED/ALL)
 * @property selectedClass kelas yang difilter (dropdown)
 * @property izinList daftar izin realtime sesuai filter
 * @property isLoading true saat stream awal belum emit
 * @property actionSuccessMessage one-shot pesan sukses (snackbar)
 * @property errorMessage one-shot error
 */
data class IzinApprovalUiState(
    val selectedFilter: ApprovalFilter = ApprovalFilter.PENDING,
    val selectedClass: String = TeacherDashboardDefaults.DEFAULT_CLASS,
    val availableClasses: List<String> = TeacherDashboardDefaults.AVAILABLE_CLASSES,
    val izinList: List<IzinRecord> = emptyList(),
    val isLoading: Boolean = true,
    val actionSuccessMessage: String? = null,
    val errorMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class IzinApprovalViewModel(
    private val izinRepository: IzinRepository,
    private val authRepository: AuthRepository,
    private val fcmNotifier: FcmApprovalNotifier? = null,
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow(ApprovalFilter.PENDING)
    private val _selectedClass = MutableStateFlow(TeacherDashboardDefaults.DEFAULT_CLASS)
    private val _actionMessage = MutableStateFlow<String?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _isApproving = MutableStateFlow(false)

    /** Allow UI to change filter tab. */
    fun onFilterSelected(filter: ApprovalFilter) {
        _selectedFilter.value = filter
    }

    fun onClassSelected(kelas: String) {
        if (kelas.isNotBlank()) _selectedClass.value = kelas
    }

    val uiState: StateFlow<IzinApprovalUiState> =
        combine(_selectedFilter, _selectedClass) { filter, kelas -> filter to kelas }
            .flatMapLatest { (filter, kelas) ->
                combine(
                    izinRepository.observeIzinList(kelas, filter.wireValue),
                    _actionMessage,
                    _errorMessage,
                ) { list, msg, err ->
                    IzinApprovalUiState(
                        selectedFilter = filter,
                        selectedClass = kelas,
                        izinList = list,
                        isLoading = false,
                        actionSuccessMessage = msg,
                        errorMessage = err,
                    )
                }
            }
            .catch { e ->
                emit(
                    IzinApprovalUiState(
                        selectedFilter = _selectedFilter.value,
                        selectedClass = _selectedClass.value,
                        isLoading = false,
                        errorMessage = e.message ?: "Gagal memuat data",
                    )
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = IzinApprovalUiState(isLoading = true),
            )

    // Dedicated flow for simpler test: expose filtered list directly
    // But spec wants viewModel with functions approve/reject.

    fun approveIzin(izinId: String) {
        if (izinId.isBlank()) {
            _errorMessage.value = "ID izin tidak valid"
            return
        }
        viewModelScope.launch {
            _isApproving.value = true
            _errorMessage.value = null
            _actionMessage.value = null
            val approverUid = authRepository.currentUid ?: ""
            val result = izinRepository.updateIzinStatus(
                izinId = izinId,
                status = ApprovalStatus.APPROVED,
                note = null,
                approverUid = approverUid,
            )
            _isApproving.value = false
            result.fold(
                onSuccess = {
                    _actionMessage.value = "Izin disetujui"
                    // Optional FCM
                    try { fcmNotifier?.notifyStatusChanged(izinId, ApprovalStatus.APPROVED) } catch (_: Exception) {}
                },
                onFailure = { e ->
                    _errorMessage.value = e.message ?: "Gagal menyetujui izin"
                },
            )
        }
    }

    fun rejectIzin(izinId: String, note: String) {
        if (izinId.isBlank()) {
            _errorMessage.value = "ID izin tidak valid"
            return
        }
        if (note.isBlank()) {
            _errorMessage.value = "Catatan penolakan wajib diisi"
            return
        }
        viewModelScope.launch {
            _isApproving.value = true
            _errorMessage.value = null
            _actionMessage.value = null
            val approverUid = authRepository.currentUid ?: ""
            val result = izinRepository.updateIzinStatus(
                izinId = izinId,
                status = ApprovalStatus.REJECTED,
                note = note.trim(),
                approverUid = approverUid,
            )
            _isApproving.value = false
            result.fold(
                onSuccess = {
                    _actionMessage.value = "Izin ditolak"
                    try { fcmNotifier?.notifyStatusChanged(izinId, ApprovalStatus.REJECTED) } catch (_: Exception) {}
                },
                onFailure = { e ->
                    _errorMessage.value = e.message ?: "Gagal menolak izin"
                },
            )
        }
    }

    fun onMessagesConsumed() {
        _actionMessage.value = null
        _errorMessage.value = null
    }

    companion object {
        fun factory(
            izinRepository: IzinRepository = FirebaseIzinRepository(),
            authRepository: AuthRepository = FirebaseAuthRepository(),
            fcmNotifier: FcmApprovalNotifier? = null,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                IzinApprovalViewModel(izinRepository, authRepository, fcmNotifier)
            }
        }
    }
}

/**
 * Optional notifier — implemented to send FCM when status changes.
 * No-op default so tests don't need Firebase.
 */
interface FcmApprovalNotifier {
    suspend fun notifyStatusChanged(izinId: String, status: ApprovalStatus)
}
