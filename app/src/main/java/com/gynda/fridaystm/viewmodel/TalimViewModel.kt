package com.gynda.fridaystm.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gynda.fridaystm.R
import com.gynda.fridaystm.data.model.TalimSummary
import com.gynda.fridaystm.data.model.User
import com.gynda.fridaystm.data.repository.AuthRepository
import com.gynda.fridaystm.data.repository.FirebaseAuthRepository
import com.gynda.fridaystm.data.repository.FirestoreTalimRepository
import com.gynda.fridaystm.data.repository.TalimRepository
import com.gynda.fridaystm.domain.Role
import com.gynda.fridaystm.util.SystemTimeProvider
import com.gynda.fridaystm.util.TimeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Drives the Ta'lim class-summary section on Home: streams
 * `talim_summaries/{date}_{kelas}` for the signed-in user's class and, for a
 * class rep/admin ([Role.canSubmitTalimSummary]), lets them submit it once.
 *
 * SKILL.md compliance: no Android/Compose types; date via [TimeProvider]; single
 * immutable `StateFlow`; events flow up as `on…`; deps are interfaces for testing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TalimViewModel(
    private val timeProvider: TimeProvider,
    private val authRepository: AuthRepository,
    private val talimRepository: TalimRepository,
) : ViewModel() {

    /** The Friday whose summary is shown/edited. Fixed per VM instance. */
    private val date: String = timeProvider.today().toString()

    private val userFlow: Flow<User?> = authRepository.observeAuthState()
        .distinctUntilChanged()
        .flatMapLatest { uid ->
            if (uid == null) flowOf(null) else authRepository.observeUserProfile(uid)
        }

    /** Latest signed-in profile, cached from the combine so [onSubmit] can stamp it. */
    private var lastUser: User? = null

    val uiState: StateFlow<TalimUiState> = userFlow
        .flatMapLatest { user ->
            lastUser = user
            val kelas = user?.kelas.orEmpty()
            val role = user?.roleEnum ?: Role.STUDENT
            val summaries =
                if (kelas.isBlank()) flowOf(null) else talimRepository.observeSummary(date, kelas)
            summaries.map { summary ->
                TalimUiState(
                    date = date,
                    kelas = kelas,
                    summary = summary,
                    canSubmit = role.canSubmitTalimSummary && kelas.isNotBlank(),
                )
            }
        }
        .catch { emit(TalimUiState(date = date)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            initialValue = TalimUiState(date = date),
        )

    private val _submitStatus = MutableStateFlow<SubmitStatus>(SubmitStatus.Idle)
    val submitStatus: StateFlow<SubmitStatus> = _submitStatus.asStateFlow()

    /**
     * Class rep submits the class summary. Blank fields are rejected client-side;
     * Firestore rules must also reject non-class-rep writes (SKILL.md §8).
     */
    fun onSubmit(penceramah: String, tema: String, ringkasan: String) {
        val user = lastUser ?: return
        if (penceramah.isBlank() || tema.isBlank() || ringkasan.isBlank()) {
            _submitStatus.value = SubmitStatus.Error(R.string.talim_error_incomplete)
            return
        }
        _submitStatus.value = SubmitStatus.Submitting
        viewModelScope.launch {
            _submitStatus.value = talimRepository.submitSummary(
                TalimSummary(
                    date = date,
                    kelas = user.kelas,
                    grade = user.grade,
                    penceramah = penceramah.trim(),
                    tema = tema.trim(),
                    ringkasan = ringkasan.trim(),
                    submittedByUid = user.uid,
                    submittedByName = user.nama,
                ),
            ).fold(
                onSuccess = { SubmitStatus.Success },
                onFailure = { SubmitStatus.Error(R.string.submit_error_generic) },
            )
        }
    }

    /** Acknowledge a shown success/error so it is not re-displayed on recomposition. */
    fun onSubmitStatusConsumed() {
        _submitStatus.value = SubmitStatus.Idle
    }

    companion object {
        private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

        fun factory(
            timeProvider: TimeProvider = SystemTimeProvider(),
            authRepository: AuthRepository = FirebaseAuthRepository(),
            talimRepository: TalimRepository = FirestoreTalimRepository(),
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { TalimViewModel(timeProvider, authRepository, talimRepository) }
        }
    }
}

/**
 * Immutable UI state for the Ta'lim class-summary section.
 *
 * @property summary the class's submitted summary, or `null` until submitted.
 * @property canSubmit class rep/admin with a known class — show the submit form.
 */
data class TalimUiState(
    val date: String,
    val kelas: String = "",
    val summary: TalimSummary? = null,
    val canSubmit: Boolean = false,
)
