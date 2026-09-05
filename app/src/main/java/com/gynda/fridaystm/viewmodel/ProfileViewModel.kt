package com.gynda.fridaystm.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gynda.fridaystm.R
import com.gynda.fridaystm.data.model.User
import com.gynda.fridaystm.data.repository.AuthRepository
import com.gynda.fridaystm.data.repository.FirebaseAuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Exhaustive UI state for the profile screen (SKILL.md §3.2). */
sealed interface ProfileUiState {
    data object Loading : ProfileUiState
    data class Ready(val user: User) : ProfileUiState
    data class Error(@StringRes val messageResId: Int) : ProfileUiState
}

/**
 * Drives [com.gynda.fridaystm.ui.screen.ProfileScreen]: streams the signed-in
 * student's profile and performs sign-out (M5.2).
 *
 * SKILL.md: no Android/Compose types; profile is a `Flow` from the repository;
 * single `StateFlow`; interface dependency for testability.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val uiState: StateFlow<ProfileUiState> = authRepository.observeAuthState()
        .distinctUntilChanged()
        .flatMapLatest { uid ->
            if (uid == null) flowOf(null) else authRepository.observeUserProfile(uid)
        }
        .map { user ->
            if (user == null) ProfileUiState.Error(R.string.profile_error_generic)
            else ProfileUiState.Ready(user)
        }
        .catch { emit(ProfileUiState.Error(R.string.profile_error_generic)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            initialValue = ProfileUiState.Loading,
        )

    fun signOut() = authRepository.signOut()

    companion object {
        private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

        fun factory(
            authRepository: AuthRepository = FirebaseAuthRepository(),
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ProfileViewModel(authRepository) }
        }
    }
}
