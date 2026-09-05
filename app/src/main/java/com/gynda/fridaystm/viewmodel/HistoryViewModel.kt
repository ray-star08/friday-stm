package com.gynda.fridaystm.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gynda.fridaystm.R
import com.gynda.fridaystm.data.model.AttendanceRecord
import com.gynda.fridaystm.data.repository.AttendanceRepository
import com.gynda.fridaystm.data.repository.AuthRepository
import com.gynda.fridaystm.data.repository.FirebaseAuthRepository
import com.gynda.fridaystm.data.repository.FirestoreAttendanceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Exhaustive UI state for the attendance history screen (SKILL.md §3.2).
 */
sealed interface HistoryUiState {

    /** Still loading the history stream. */
    data object Loading : HistoryUiState

    /** Loaded, has at least one record (most-recent first). */
    data class Success(val records: List<AttendanceRecord>) : HistoryUiState

    /** Loaded, but the user has no attendance records yet. */
    data object Empty : HistoryUiState

    /** A load failure; [messageResId] is resolved to text in the Composable. */
    data class Error(@StringRes val messageResId: Int) : HistoryUiState
}

/**
 * Drives [com.gynda.fridaystm.ui.screen.HistoryScreen]: streams the signed-in
 * user's attendance records, newest first (M5.1).
 *
 * SKILL.md compliance: no Android/Compose types here; the repository stream is a
 * `Flow` wrapping a Firestore snapshot listener (no raw callbacks leak up); state
 * is a single `StateFlow`; dependencies are interfaces so it is unit-testable.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    authRepository: AuthRepository,
    attendanceRepository: AttendanceRepository,
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = authRepository.observeAuthState()
        .distinctUntilChanged()
        .flatMapLatest { uid ->
            if (uid == null) flowOf<List<AttendanceRecord>>(emptyList())
            else attendanceRepository.observeHistory(uid)
        }
        .map { records ->
            if (records.isEmpty()) HistoryUiState.Empty
            else HistoryUiState.Success(records)
        }
        .catch { emit(HistoryUiState.Error(R.string.history_error_generic)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            initialValue = HistoryUiState.Loading,
        )

    companion object {
        private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

        fun factory(
            authRepository: AuthRepository = FirebaseAuthRepository(),
            attendanceRepository: AttendanceRepository = FirestoreAttendanceRepository(),
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { HistoryViewModel(authRepository, attendanceRepository) }
        }
    }
}
