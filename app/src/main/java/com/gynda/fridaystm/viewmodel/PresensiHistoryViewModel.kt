package com.gynda.fridaystm.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gynda.fridaystm.data.model.PresensiRecord
import com.gynda.fridaystm.data.repository.AuthRepository
import com.gynda.fridaystm.data.repository.FirebaseAuthRepository
import com.gynda.fridaystm.data.repository.FirebasePresensiRepository
import com.gynda.fridaystm.data.repository.PresensiRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * UI state for presensi history screen.
 */
sealed interface PresensiHistoryUiState {
    data object Loading : PresensiHistoryUiState
    data class Success(val records: List<PresensiRecord>) : PresensiHistoryUiState
    data object Empty : PresensiHistoryUiState
    data class Error(val message: String) : PresensiHistoryUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class PresensiHistoryViewModel(
    authRepository: AuthRepository,
    private val presensiRepository: PresensiRepository,
) : ViewModel() {

    private val refreshTrigger = MutableStateFlow(0)
    private val isRefreshing = MutableStateFlow(false)

    val uiState: StateFlow<PresensiHistoryUiState> = combine(
        authRepository.observeAuthState().distinctUntilChanged(),
        refreshTrigger
    ) { uid, _ -> uid }
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(Result.success(emptyList<PresensiRecord>()))
            } else {
                presensiRepository.getPresensiHistory(uid)
            }
        }
        .map { result ->
            isRefreshing.update { false }
            result.fold(
                onSuccess = { list ->
                    if (list.isEmpty()) PresensiHistoryUiState.Empty
                    else PresensiHistoryUiState.Success(list)
                },
                onFailure = { e ->
                    PresensiHistoryUiState.Error(e.message ?: "Gagal memuat riwayat")
                }
            )
        }
        .catch { e -> emit(PresensiHistoryUiState.Error(e.message ?: "Gagal memuat riwayat")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PresensiHistoryUiState.Loading
        )

    val refreshing: StateFlow<Boolean> = isRefreshing

    fun refresh() {
        isRefreshing.value = true
        refreshTrigger.update { it + 1 }
    }

    companion object {
        fun factory(
            authRepository: AuthRepository = FirebaseAuthRepository(),
            presensiRepository: PresensiRepository = FirebasePresensiRepository(),
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { PresensiHistoryViewModel(authRepository, presensiRepository) }
        }
    }
}
