package com.gynda.fridaystm.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gynda.fridaystm.data.model.ProfileStats
import com.gynda.fridaystm.data.model.User
import com.gynda.fridaystm.data.repository.AuthRepository
import com.gynda.fridaystm.data.repository.FirebaseAuthRepository
import com.gynda.fridaystm.data.repository.FirebaseProfileStatsRepository
import com.gynda.fridaystm.data.repository.ProfileStatsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * Exhaustive UI state layar Profil (SKILL.md §3.2).
 *
 * Pesan [Error] berupa [String] (bukan `@StringRes`) seperti
 * `PresensiCameraUiState.Error`, agar ViewModel tetap murni-JVM dan pesannya
 * bisa di-assert langsung di unit test.
 */
sealed interface UserProfileUiState {
    data object Loading : UserProfileUiState

    /**
     * @property user profil dari `users/{uid}` (NIS, nama, kelas, photoUrl).
     * @property email email sesi Firebase Auth (info akun).
     * @property stats agregat presensi / larkam / izin.
     */
    data class Success(
        val user: User,
        val email: String,
        val stats: ProfileStats,
    ) : UserProfileUiState

    data class Error(val message: String) : UserProfileUiState
}

/**
 * Drives `ProfileScreen`: memuat profil + statistik ringkasan siswa yang
 * sedang masuk, dan menangani keluar akun.
 *
 * SKILL.md: tanpa tipe Android/Compose; single `StateFlow` untuk state;
 * dependensi berupa interface agar testable. Navigasi tidak dipegang
 * ViewModel — [logoutEvent] dikoleksi UI dan diteruskan sebagai callback
 * `onLogout` ke NavHost (yang mengosongkan backstack ke Login).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val statsRepository: ProfileStatsRepository,
) : ViewModel() {

    val uiState: StateFlow<UserProfileUiState> = authRepository.observeAuthState()
        .distinctUntilChanged()
        .flatMapLatest { uid -> profileFlow(uid) }
        .catch { emit(UserProfileUiState.Error(MSG_LOAD_FAILED)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            initialValue = UserProfileUiState.Loading,
        )

    private val _logoutEvent = MutableStateFlow(false)
    /** `true` sekali setelah [logout] — UI menavigasi ke Login lalu mengonsumsi via [onLogoutConsumed]. */
    val logoutEvent: StateFlow<Boolean> = _logoutEvent.asStateFlow()

    /**
     * Keluar akun: sign-out Firebase, bersihkan state ke [UserProfileUiState.Loading],
     * dan pancarkan [logoutEvent] agar UI menavigasi ke Login.
     */
    fun logout() {
        authRepository.signOut()
        _logoutEvent.value = true
    }

    /** Konsumsi event logout setelah navigasi agar tak terpicu ulang saat recomposition. */
    fun onLogoutConsumed() {
        _logoutEvent.value = false
    }

    private fun profileFlow(uid: String?): Flow<UserProfileUiState> = flow {
        if (uid == null) {
            emit(UserProfileUiState.Error(MSG_SIGNED_OUT))
            return@flow
        }
        emit(UserProfileUiState.Loading)
        val user = authRepository.getUserProfile(uid)
            .getOrElse {
                emit(UserProfileUiState.Error(MSG_LOAD_FAILED))
                return@flow
            }
        val stats = statsRepository.getStats(uid).getOrDefault(ProfileStats())
        emit(
            UserProfileUiState.Success(
                user = user,
                email = authRepository.currentEmail.orEmpty(),
                stats = stats,
            ),
        )
    }

    companion object {
        private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

        const val MSG_LOAD_FAILED = "Gagal memuat profil. Coba lagi."
        const val MSG_SIGNED_OUT = "Sesi berakhir. Silakan login ulang."

        fun factory(
            authRepository: AuthRepository = FirebaseAuthRepository(),
            statsRepository: ProfileStatsRepository = FirebaseProfileStatsRepository(),
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ProfileViewModel(authRepository, statsRepository) }
        }
    }
}
