package com.gynda.fridaystm.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gynda.fridaystm.R
import com.gynda.fridaystm.data.model.LarkamRun
import com.gynda.fridaystm.data.model.User
import com.gynda.fridaystm.data.repository.AuthRepository
import com.gynda.fridaystm.data.repository.DefaultLarkamRepository
import com.gynda.fridaystm.data.repository.FirebaseAuthRepository
import com.gynda.fridaystm.data.repository.LarkamRepository
import com.gynda.fridaystm.domain.distanceMeters
import com.gynda.fridaystm.util.LocationProvider
import com.gynda.fridaystm.util.TimeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime

/**
 * Drives the Larkam run tracker: polls the device fix on a fixed cadence, sums
 * the haversine distance between consecutive fixes ([distanceMeters]) and ticks
 * elapsed time from the injected [TimeProvider]; on stop, persists the run to
 * Firestore through [LarkamRepository] (no external sync).
 *
 * Reuses the single-shot [LocationProvider.currentLocation] via polling rather
 * than adding a location-stream dependency (SKILL.md §9 — smallest surface). All
 * types are plain JVM so the loop is unit-testable with a fake fix + virtual time.
 * Mock-provided fixes are dropped (SKILL.md §8, fake-GPS).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LarkamViewModel(
    private val timeProvider: TimeProvider,
    private val authRepository: AuthRepository,
    private val locationProvider: LocationProvider,
    private val larkamRepository: LarkamRepository,
    private val pollIntervalMs: Long = DEFAULT_POLL_MS,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LarkamUiState())
    val uiState: StateFlow<LarkamUiState> = _uiState.asStateFlow()

    private var lastUser: User? = null
    private var trackingJob: Job? = null
    private var startTime: LocalDateTime? = null
    private var lastPoint: RunPoint? = null

    init {
        viewModelScope.launch {
            authRepository.observeAuthState()
                .distinctUntilChanged()
                .flatMapLatest { uid ->
                    if (uid == null) flowOf(null) else authRepository.observeUserProfile(uid)
                }
                .collect { lastUser = it }
        }
    }

    /** Begin tracking. No-op if already running. */
    fun onStart() {
        if (_uiState.value.status == RunStatus.Running) return
        startTime = timeProvider.now()
        lastPoint = null
        _uiState.value = LarkamUiState(status = RunStatus.Running)
        trackingJob = viewModelScope.launch {
            while (isActive) {
                tick()
                delay(pollIntervalMs)
            }
        }
    }

    private suspend fun tick() {
        val start = startTime ?: return
        val elapsed = Duration.between(start, timeProvider.now()).seconds
        val fix = locationProvider.currentLocation().getOrNull()?.takeIf { !it.isMock }
        val cur = _uiState.value
        if (fix == null) {
            _uiState.value = cur.copy(elapsedSec = elapsed) // still advance the clock
            return
        }
        val prev = lastPoint
        val added = if (prev != null) distanceMeters(prev.lat, prev.lng, fix.lat, fix.lng) else 0.0
        val point = RunPoint(fix.lat, fix.lng)
        lastPoint = point
        _uiState.value = cur.copy(
            elapsedSec = elapsed,
            distanceMeters = cur.distanceMeters + added,
            path = cur.path + point,
        )
    }

    /** Stop tracking and persist the run. No-op unless currently running. */
    fun onStop() {
        val snapshot = _uiState.value
        if (snapshot.status != RunStatus.Running) return
        trackingJob?.cancel()
        _uiState.value = snapshot.copy(status = RunStatus.Saving)
        viewModelScope.launch {
            val user = lastUser
            if (user == null) {
                _uiState.value = snapshot.copy(status = RunStatus.Error(R.string.submit_error_generic))
                return@launch
            }
            val run = LarkamRun(
                userId = user.uid,
                distanceMeters = snapshot.distanceMeters,
                elapsedSec = snapshot.elapsedSec,
                path = snapshot.path.map { mapOf("lat" to it.lat, "lng" to it.lng) },
            )
            _uiState.value = larkamRepository.logRun(run).fold(
                onSuccess = { snapshot.copy(status = RunStatus.Saved) },
                onFailure = { snapshot.copy(status = RunStatus.Error(R.string.submit_error_generic)) },
            )
        }
    }

    companion object {
        // ponytail: fixed 3s poll — good enough for a campus jog; make it adaptive
        // (speed-based) only if battery/precision becomes a real complaint.
        private const val DEFAULT_POLL_MS = 3_000L

        fun factory(
            locationProvider: LocationProvider,
            timeProvider: TimeProvider = com.gynda.fridaystm.util.SystemTimeProvider(),
            authRepository: AuthRepository = FirebaseAuthRepository(),
            larkamRepository: LarkamRepository = DefaultLarkamRepository(),
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LarkamViewModel(timeProvider, authRepository, locationProvider, larkamRepository)
            }
        }
    }
}

/** One tracked GPS point (plain lat/lng; the map layer converts to osmdroid). */
data class RunPoint(val lat: Double, val lng: Double)

sealed interface RunStatus {
    data object Idle : RunStatus
    data object Running : RunStatus
    data object Saving : RunStatus
    data object Saved : RunStatus
    data class Error(@StringRes val messageResId: Int) : RunStatus
}

/**
 * Immutable UI state for the Larkam tracker.
 *
 * @property elapsedSec whole seconds since start.
 * @property distanceMeters accumulated haversine distance.
 * @property path fixes so far, for the map polyline.
 */
data class LarkamUiState(
    val status: RunStatus = RunStatus.Idle,
    val elapsedSec: Long = 0,
    val distanceMeters: Double = 0.0,
    val path: List<RunPoint> = emptyList(),
)
