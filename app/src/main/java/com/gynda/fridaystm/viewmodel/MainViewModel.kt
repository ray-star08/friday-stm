package com.gynda.fridaystm.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gynda.fridaystm.data.repository.AuthRepository
import com.gynda.fridaystm.data.repository.FirebaseAuthRepository
import com.gynda.fridaystm.domain.Role
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Role-aware landing resolver for Splash/Login.
 *
 * Observes auth → profile → role, so both Splash and Login can redirect
 * without duplicating the `student vs guru/admin` check.
 *
 * SKILL.md: no Android/Compose types, interface dependency, single StateFlow.
 */
sealed interface MainNavTarget {
    data object Loading : MainNavTarget
    data object Login : MainNavTarget
    data object StudentHome : MainNavTarget
    data object TeacherDashboard : MainNavTarget
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val navTarget: StateFlow<MainNavTarget> = authRepository.observeAuthState()
        .distinctUntilChanged()
        .flatMapLatest { uid ->
            if (uid == null) flowOf(MainNavTarget.Login)
            else authRepository.observeUserProfile(uid).map { user ->
                when {
                    user == null -> MainNavTarget.Login
                    user.roleEnum.isTeacherOrAdmin -> MainNavTarget.TeacherDashboard
                    else -> MainNavTarget.StudentHome
                }
            }
        }
        .catch { emit(MainNavTarget.Login) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MainNavTarget.Loading,
        )

    /** Convenience for Login's onSuccess: one-shot fetch of current role's target. */
    suspend fun resolvePostLoginTarget(): MainNavTarget {
        val uid = authRepository.currentUid ?: return MainNavTarget.Login
        val user = authRepository.getUserProfile(uid).getOrNull() ?: return MainNavTarget.Login
        return if (user.roleEnum.isTeacherOrAdmin) MainNavTarget.TeacherDashboard
        else MainNavTarget.StudentHome
    }

    /** Expose role check for tests without needing flow. */
    fun isTeacherRole(role: String): Boolean {
        val enum = com.gynda.fridaystm.domain.roleFromWire(role)
        return enum.isTeacherOrAdmin
    }

    companion object {
        fun factory(
            authRepository: AuthRepository = FirebaseAuthRepository(),
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { MainViewModel(authRepository) }
        }
    }
}
