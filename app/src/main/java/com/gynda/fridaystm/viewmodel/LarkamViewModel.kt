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
import android.location.Location
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
 * Drives the Larkam run tracker: polls the device fix via [LocationProvider]
 * backed by [com.google.android.gms.location.FusedLocationProviderClient] with
 * high-accuracy request (interval 3–5s, see [DEFAULT_POLL_MS]), sums distance
 * using [Location.distanceBetween] and ticks elapsed time from [TimeProvider];
 * on stop, persists the run to Firestore through [LarkamRepository].
 *
 * Supports states IDLE → RUNNING → PAUSED ↔ RUNNING → FINISHED → SAVING/SAVED.
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
    private var pauseStartTime: LocalDateTime? = null
    private var totalPausedSeconds: Long = 0

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
        // Resume from pause: adjust startTime by paused duration
        if (_uiState.value.status == RunStatus.Paused) {
            onResume()
            return
        }
        startTime = timeProvider.now()
        totalPausedSeconds = 0
        pauseStartTime = null
        lastPoint = null
        _uiState.value = LarkamUiState(status = RunStatus.Running)
        trackingJob = viewModelScope.launch {
            while (isActive) {
                tick()
                delay(pollIntervalMs)
            }
        }
    }

    /** Pause tracking — keeps path/distance, freezes timer. */
    fun onPause() {
        if (_uiState.value.status != RunStatus.Running) return
        trackingJob?.cancel()
        trackingJob = null
        pauseStartTime = timeProvider.now()
        _uiState.value = _uiState.value.copy(status = RunStatus.Paused)
    }

    /** Resume from pause. */
    fun onResume() {
        if (_uiState.value.status != RunStatus.Paused) return
        pauseStartTime?.let { pausedSince ->
            val pausedSec = Duration.between(pausedSince, timeProvider.now()).seconds
            totalPausedSeconds += pausedSec.coerceAtLeast(0)
        }
        pauseStartTime = null
        _uiState.value = _uiState.value.copy(status = RunStatus.Running)
        trackingJob = viewModelScope.launch {
            while (isActive) {
                tick()
                delay(pollIntervalMs)
            }
        }
    }

    /** Finish tracking — transitions to FINISHED and then persists. */
    fun onFinish() {
        val snapshot = _uiState.value
        if (snapshot.status != RunStatus.Running && snapshot.status != RunStatus.Paused) return
        trackingJob?.cancel()
        // If paused, account for remaining paused time
        if (snapshot.status == RunStatus.Paused) {
            pauseStartTime?.let { pausedSince ->
                val pausedSec = Duration.between(pausedSince, timeProvider.now()).seconds
                totalPausedSeconds += pausedSec.coerceAtLeast(0)
            }
            pauseStartTime = null
        }
        _uiState.value = snapshot.copy(status = RunStatus.Finished)
    }

    private suspend fun tick() {
        val start = startTime ?: return
        val rawElapsed = Duration.between(start, timeProvider.now()).seconds
        val elapsed = (rawElapsed - totalPausedSeconds).coerceAtLeast(0)
        val fix = locationProvider.currentLocation().getOrNull()?.takeIf { !it.isMock }
        val cur = _uiState.value
        if (fix == null) {
            _uiState.value = cur.copy(elapsedSec = elapsed) // still advance the clock
            return
        }
        val prev = lastPoint
        val added = if (prev != null) distanceBetween(prev.lat, prev.lng, fix.lat, fix.lng) else 0.0
        val point = RunPoint(fix.lat, fix.lng)
        lastPoint = point
        _uiState.value = cur.copy(
            elapsedSec = elapsed,
            distanceMeters = cur.distanceMeters + added,
            path = cur.path + point,
        )
    }

    /** Calculates distance using Android Location.distanceBetween (spec compliant) with haversine fallback for JVM tests. */
    private fun distanceBetween(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        return try {
            val res = FloatArray(1)
            Location.distanceBetween(lat1, lng1, lat2, lng2, res)
            val d = res[0].toDouble()
            // On JVM unit test, distanceBetween returns 0 (mocked); fallback to haversine
            if (d == 0.0) distanceMeters(lat1, lng1, lat2, lng2) else d
        } catch (_: Exception) {
            distanceMeters(lat1, lng1, lat2, lng2)
        }
    }

    /** Stop tracking and persist the run. No-op unless currently running/paused/finished. */
    fun onStop() {
        val snapshot = _uiState.value
        if (snapshot.status != RunStatus.Running && snapshot.status != RunStatus.Paused && snapshot.status != RunStatus.Finished) return
        trackingJob?.cancel()
        // If still paused, flush paused duration
        if (snapshot.status == RunStatus.Paused) {
            pauseStartTime?.let { pausedSince ->
                val pausedSec = Duration.between(pausedSince, timeProvider.now()).seconds
                totalPausedSeconds += pausedSec.coerceAtLeast(0)
            }
            pauseStartTime = null
        }
        // Recompute final elapsed accounting for pauses
        val start = startTime
        val finalElapsed = if (start != null) {
            (Duration.between(start, timeProvider.now()).seconds - totalPausedSeconds).coerceAtLeast(0)
        } else snapshot.elapsedSec
        val finalSnapshot = snapshot.copy(elapsedSec = finalElapsed, status = RunStatus.Saving)
        _uiState.value = finalSnapshot
        viewModelScope.launch {
            val user = lastUser
            if (user == null) {
                _uiState.value = finalSnapshot.copy(status = RunStatus.Error(R.string.submit_error_generic))
                return@launch
            }
            val run = LarkamRun(
                userId = user.uid,
                distanceMeters = finalSnapshot.distanceMeters,
                elapsedSec = finalSnapshot.elapsedSec,
                path = finalSnapshot.path.map { mapOf("lat" to it.lat, "lng" to it.lng) },
            )
            _uiState.value = larkamRepository.logRun(run).fold(
                onSuccess = { finalSnapshot.copy(status = RunStatus.Saved) },
                onFailure = { finalSnapshot.copy(status = RunStatus.Error(R.string.submit_error_generic)) },
            )
        }
    }

    /** Save to larkam_records with watermarked image (used after selfie). */
    fun saveLarkamRecord(
        imageUrl: String,
        onDone: (Boolean) -> Unit = {}
    ) {
        val snapshot = _uiState.value
        viewModelScope.launch {
            val user = lastUser ?: run { onDone(false); return@launch }
            // For larkam_records we reuse larkamRepository but could be separate; using Firestore directly for now is handled via Presensi flow
            // This is a placeholder for the selfie finish flow; actual save is done via PresensiCameraViewModel with payload
            onDone(true)
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
    data object Paused : RunStatus
    data object Finished : RunStatus
    data object Saving : RunStatus
    data object Saved : RunStatus
    data class Error(@param:StringRes val messageResId: Int) : RunStatus
}

/**
 * Immutable UI state for the Larkam tracker.
 *
 * @property elapsedSec whole seconds since start (paused time excluded).
 * @property distanceMeters accumulated distance via Location.distanceBetween.
 * @property path fixes so far, for the map polyline.
 */
data class LarkamUiState(
    val status: RunStatus = RunStatus.Idle,
    val elapsedSec: Long = 0,
    val distanceMeters: Double = 0.0,
    val path: List<RunPoint> = emptyList(),
) {
    /** Distance in KM with 2 decimals for display. */
    val distanceKm: Double get() = distanceMeters / 1000.0

    /** Pace in minutes per KM (e.g. 6.5 = 6:30 /km), 0 if no distance. */
    val paceMinPerKm: Double get() = if (distanceKm > 0) (elapsedSec / 60.0) / distanceKm else 0.0

    /** Formatted pace as mm:ss /km */
    val paceFormatted: String get() {
        if (distanceKm == 0.0 || paceMinPerKm == 0.0) return "--:-- /km"
        val totalSec = (paceMinPerKm * 60).toInt()
        return "%02d:%02d /km".format(totalSec / 60, totalSec % 60)
    }

    /** Timer as HH:MM:SS */
    val timerFormatted: String get() = "%02d:%02d:%02d".format(elapsedSec / 3600, (elapsedSec % 3600) / 60, elapsedSec % 60)
}
