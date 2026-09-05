package com.gynda.fridaystm.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gynda.fridaystm.R
import com.gynda.fridaystm.data.model.SenamSession
import com.gynda.fridaystm.data.model.User
import com.gynda.fridaystm.data.repository.AuthRepository
import com.gynda.fridaystm.data.repository.FirebaseAuthRepository
import com.gynda.fridaystm.data.repository.FirestoreSenamRepository
import com.gynda.fridaystm.data.repository.SenamRepository
import com.gynda.fridaystm.domain.Role
import com.gynda.fridaystm.domain.extractYouTubeId
import com.gynda.fridaystm.util.SystemTimeProvider
import com.gynda.fridaystm.util.TimeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Drives the weekly Senam section on Home: streams `senam_sessions/{weekId}` and,
 * for an instructor/admin, lets them set the week's YouTube video.
 *
 * SKILL.md compliance: no Android/Compose types; time via [TimeProvider]; single
 * immutable `StateFlow`; events flow up as `on…`; deps are interfaces for testing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SenamViewModel(
    private val timeProvider: TimeProvider,
    private val authRepository: AuthRepository,
    private val senamRepository: SenamRepository,
) : ViewModel() {

    /** The week whose Senam video is shown/edited. Fixed per VM instance. */
    private val weekId: String = timeProvider.weekId()

    private val userFlow: Flow<User?> = authRepository.observeAuthState()
        .distinctUntilChanged()
        .flatMapLatest { uid ->
            if (uid == null) flowOf(null) else authRepository.observeUserProfile(uid)
        }

    private val sessionFlow: Flow<SenamSession?> = senamRepository.observeSession(weekId)

    /** Latest signed-in profile, cached from the combine so [onSetVideo] can stamp it. */
    private var lastUser: User? = null

    val uiState: StateFlow<SenamUiState> = combine(userFlow, sessionFlow) { user, session ->
        lastUser = user
        val role = user?.roleEnum ?: Role.STUDENT
        SenamUiState(
            weekId = weekId,
            session = session,
            canManage = role.canManageSenam,
            canView = role.canViewSenam,
        )
    }
        .catch { emit(SenamUiState(weekId = weekId)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            initialValue = SenamUiState(weekId = weekId),
        )

    private val _setStatus = MutableStateFlow<SubmitStatus>(SubmitStatus.Idle)
    val setStatus: StateFlow<SubmitStatus> = _setStatus.asStateFlow()

    /**
     * Instructor sets the week's Senam video from a pasted URL (or bare id). The
     * id is extracted client-side; an unrecognized URL surfaces an error without a
     * write. Firestore rules must also reject non-instructor writes (SKILL.md §8).
     */
    fun onSetVideo(url: String) {
        val user = lastUser ?: return
        val videoId = extractYouTubeId(url)
        if (videoId == null) {
            _setStatus.value = SubmitStatus.Error(R.string.senam_error_bad_url)
            return
        }
        _setStatus.value = SubmitStatus.Submitting
        viewModelScope.launch {
            _setStatus.value = senamRepository.setSession(
                SenamSession(
                    weekId = weekId,
                    videoId = videoId,
                    setByUid = user.uid,
                    setByName = user.nama,
                ),
            ).fold(
                onSuccess = { SubmitStatus.Success },
                onFailure = { SubmitStatus.Error(R.string.submit_error_generic) },
            )
        }
    }

    /** Acknowledge a shown success/error so it is not re-displayed on recomposition. */
    fun onSetStatusConsumed() {
        _setStatus.value = SubmitStatus.Idle
    }

    companion object {
        private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

        fun factory(
            timeProvider: TimeProvider = SystemTimeProvider(),
            authRepository: AuthRepository = FirebaseAuthRepository(),
            senamRepository: SenamRepository = FirestoreSenamRepository(),
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { SenamViewModel(timeProvider, authRepository, senamRepository) }
        }
    }
}

/**
 * Immutable UI state for the Senam section.
 *
 * @property session the week's video, or `null` until an instructor sets one.
 * @property canManage instructor/admin — show the set-video form.
 * @property canView everyone except a plain instructor — show the player.
 */
data class SenamUiState(
    val weekId: String,
    val session: SenamSession? = null,
    val canManage: Boolean = false,
    val canView: Boolean = true,
)
