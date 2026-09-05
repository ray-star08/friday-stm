package com.gynda.fridaystm.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gynda.fridaystm.R
import com.gynda.fridaystm.data.repository.AuthRepository
import com.gynda.fridaystm.data.repository.FirebaseAuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State for the login form (M5.2). A single data class: the field values plus the
 * transient submit outcome, kept immutable and driven from the ViewModel.
 *
 * @property submitting a sign-in call is in flight (disables the button).
 * @property errorResId set when the last attempt failed; cleared on next edit.
 * @property success sign-in succeeded — the holder navigates once, then it stays.
 */
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val submitting: Boolean = false,
    @StringRes val errorResId: Int? = null,
    val success: Boolean = false,
) {
    /** The button is only actionable with both fields filled and no call in flight. */
    val canSubmit: Boolean get() = email.isNotBlank() && password.isNotBlank() && !submitting
}

/**
 * Drives [com.gynda.fridaystm.ui.screen.LoginScreen]: holds the form fields and
 * performs email/password sign-in via [AuthRepository] (M5.2).
 *
 * SKILL.md: no Android/Compose types; `suspend` sign-in returning `Result`;
 * single `StateFlow`; interface dependency so it is unit-testable.
 *
 * ponytail: NIS-based login (task 5.2 "NIS/email") not wired — needs a NIS→email
 * lookup. Add when the users collection exposes a queryable `nis` field; the form
 * accepts email for now.
 */
class LoginViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, errorResId = null) }

    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, errorResId = null) }

    fun onSubmit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        _uiState.update { it.copy(submitting = true, errorResId = null) }
        viewModelScope.launch {
            val result = authRepository.signIn(state.email.trim(), state.password)
            _uiState.update {
                result.fold(
                    onSuccess = { _ -> it.copy(submitting = false, success = true) },
                    onFailure = { _ -> it.copy(submitting = false, errorResId = R.string.login_error_invalid) },
                )
            }
        }
    }

    companion object {
        fun factory(
            authRepository: AuthRepository = FirebaseAuthRepository(),
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { LoginViewModel(authRepository) }
        }
    }
}
