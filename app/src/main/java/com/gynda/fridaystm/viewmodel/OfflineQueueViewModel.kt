package com.gynda.fridaystm.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gynda.fridaystm.data.local.PendingPresensiStore
import com.gynda.fridaystm.data.repository.AuthRepository
import com.gynda.fridaystm.data.repository.FirebaseAuthRepository
import com.gynda.fridaystm.util.PresensiSyncScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * Drives the Dashboard offline-queue banner: live count of unsynced presensi
 * rows for the signed-in user, plus a manual "sync now" trigger.
 *
 * SKILL.md: no Android/Compose types; single `StateFlow`; interface
 * dependencies for testability. The count stream comes straight from Room
 * (`observePendingCount`), so the banner appears/disappears with no refresh.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineQueueViewModel(
    authRepository: AuthRepository,
    queue: PendingPresensiStore,
    private val syncScheduler: PresensiSyncScheduler,
) : ViewModel() {

    val pendingCount: StateFlow<Int> = authRepository.observeAuthState()
        .distinctUntilChanged()
        .flatMapLatest { uid ->
            if (uid == null) flowOf(0) else queue.observePendingCount(uid)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            initialValue = 0,
        )

    /** Manual retry from the banner — re-enqueues the constrained drain (KEEP collapses duplicates). */
    fun syncNow() = syncScheduler.schedulePresensiSync()

    companion object {
        private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

        fun factory(
            authRepository: AuthRepository = FirebaseAuthRepository(),
            queue: PendingPresensiStore,
            syncScheduler: PresensiSyncScheduler,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { OfflineQueueViewModel(authRepository, queue, syncScheduler) }
        }
    }
}
