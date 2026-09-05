package com.gynda.fridaystm.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gynda.fridaystm.R
import com.gynda.fridaystm.data.model.AttendanceRecord
import com.gynda.fridaystm.data.model.CheckoutStamp
import com.gynda.fridaystm.data.model.Geofence
import com.gynda.fridaystm.data.model.PembiasaanStamp
import com.gynda.fridaystm.data.model.RotationSchedule
import com.gynda.fridaystm.data.model.User
import com.gynda.fridaystm.data.model.activityFromWire
import com.gynda.fridaystm.data.model.toWireValue
import com.gynda.fridaystm.data.repository.AttendanceRepository
import com.gynda.fridaystm.data.repository.AuthRepository
import com.gynda.fridaystm.data.repository.FirebaseAuthRepository
import com.gynda.fridaystm.data.repository.FirestoreAttendanceRepository
import com.gynda.fridaystm.data.repository.FirestoreGeofenceRepository
import com.gynda.fridaystm.data.repository.FirestoreRotationRepository
import com.gynda.fridaystm.data.repository.GeofenceRepository
import com.gynda.fridaystm.data.repository.RotationRepository
import com.gynda.fridaystm.domain.Activity
import com.gynda.fridaystm.domain.FridayPhase
import com.gynda.fridaystm.domain.activityForGrade
import com.gynda.fridaystm.domain.distanceMeters
import com.gynda.fridaystm.domain.isInsideGeofence
import com.gynda.fridaystm.domain.resolvePhase
import com.gynda.fridaystm.util.LocationFix
import com.gynda.fridaystm.util.LocationProvider
import com.gynda.fridaystm.util.SelfiePhase
import com.gynda.fridaystm.util.SystemTimeProvider
import com.gynda.fridaystm.util.TimeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

/**
 * Drives the Home screen: merges the time-based Friday phase, the signed-in
 * student's profile, the weekly rotation and today's attendance into one
 * [HomeUiState].
 *
 * SKILL.md compliance:
 * - No Android framework / Compose types here; time comes only via [TimeProvider].
 * - State is a single immutable `StateFlow<HomeUiState>`; events flow up as `on…`
 *   handlers; network work runs in [viewModelScope] and returns `Result`.
 * - Dependencies are interfaces so this class is unit-testable with fakes (§9).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val timeProvider: TimeProvider,
    private val authRepository: AuthRepository,
    private val attendanceRepository: AttendanceRepository,
    private val geofenceRepository: GeofenceRepository,
    private val rotationRepository: RotationRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    /**
     * Ticking phase source. Re-evaluates the pure `resolvePhase` every
     * [PHASE_TICK_MS] against injected time, so the 06:30 Apel→Pembiasaan flip
     * happens on its own. `distinctUntilChanged` keeps downstream recomputation
     * to actual phase changes, not every tick.
     */
    private val phaseFlow: Flow<FridayPhase> = flow {
        while (true) {
            emit(resolvePhase(timeProvider.now()))
            delay(PHASE_TICK_MS)
        }
    }.distinctUntilChanged()

    /**
     * One shared auth-state stream feeding both the profile and record flows, so
     * we open a single Firebase auth listener. Re-emits uid on sign-in/sign-out.
     */
    private val uidFlow: Flow<String?> = authRepository.observeAuthState()
        .distinctUntilChanged()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), replay = 1)

    private val userFlow: Flow<User?> = uidFlow.flatMapLatest { uid ->
        if (uid == null) flowOf(null) else authRepository.observeUserProfile(uid)
    }

    private val recordFlow: Flow<AttendanceRecord?> = uidFlow.flatMapLatest { uid ->
        if (uid == null) flowOf(null)
        else attendanceRepository.observeTodayRecord(uid, todayIso())
    }

    /** Read-only reference fences; empty until the collection loads. */
    private val geofencesFlow: Flow<List<Geofence>> = geofenceRepository.observeGeofences()

    /**
     * The optional Firestore rotation override for the current week (task.md 3.2),
     * `null` when none exists — which is the normal case, since the cyclic formula
     * covers every ordinary week.
     *
     * The week key is read at subscription time rather than re-derived per tick: an
     * override is published for a *coming* Friday, and the screen is recreated many
     * times before a week boundary is crossed mid-session.
     *
     * `onStart { emit(null) }` makes this **non-blocking**: the screen renders from
     * the cyclic formula immediately instead of showing a spinner until Firestore
     * (or its offline cache) answers. When a real override lands it recomputes in
     * place and the "Jadwal Khusus" badge appears. This is the loading decision for
     * 3.3 — the optional override never gates [HomeUiState.Loading].
     *
     * ponytail: single-week granularity. If a holiday ever needs to span weeks,
     * make the repository resolve a date range instead of one document id.
     */
    private val rotationFlow: Flow<RotationSchedule?> = flow {
        emitAll(rotationRepository.observeActiveSchedule(timeProvider.weekId()))
    }.onStart { emit(null) }

    /**
     * The two read-only reference streams, merged so the state [combine] below
     * stays within Kotlin's 5-flow built-in overload. Both are cheap, cached
     * Firestore reference data with the same lifetime.
     */
    private val referenceFlow: Flow<Pair<List<Geofence>, RotationSchedule?>> =
        combine(geofencesFlow, rotationFlow) { fences, rotation -> fences to rotation }

    /**
     * Periodic location fix for the realtime distance + geofence gate. Polls only
     * while the state is collected (`WhileSubscribed`), so no background battery
     * drain when the screen is gone. A failed fix emits `null` → button stays
     * disabled; a **mock** fix escalates to [HomeUiState.Error] in [buildState]
     * (fail-closed, SKILL.md §8).
     */
    private val locationFlow: Flow<LocationFix?> = flow {
        while (true) {
            emit(locationProvider.currentLocation().getOrNull())
            delay(LOCATION_POLL_MS)
        }
    }.distinctUntilChanged()

    /**
     * The screen state. The rotation is a pure function of grade + week, with an
     * optional Firestore override streamed in via [referenceFlow].
     */
    val uiState: StateFlow<HomeUiState> = combine(
        phaseFlow,
        userFlow,
        recordFlow,
        referenceFlow,
        locationFlow,
    ) { phase, user, record, reference, fix ->
        val (geofences, rotation) = reference
        if (user == null) HomeUiState.SignedOut
        else buildState(phase, user, record, geofences, rotation, fix)
    }
        .catch { emit(HomeUiState.Error(R.string.home_error_generic)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            initialValue = HomeUiState.Loading,
        )

    private val _submitStatus = MutableStateFlow<SubmitStatus>(SubmitStatus.Idle)
    val submitStatus: StateFlow<SubmitStatus> = _submitStatus.asStateFlow()

    /**
     * The most recent location fix, cached from the polling [locationFlow] as the
     * state is built. Used to stamp lat/lng on a check-in write when the selfie
     * returns from the camera flow, without re-fetching. `null` until a fix lands.
     */
    private var lastFix: LocationFix? = null

    // --- Events (flow up) -------------------------------------------------

    /**
     * The camera flow finished and produced a Cloudinary `secure_url`. Submit the
     * check-in for whichever phase is currently active, stamping the last known
     * fix and marking `valid` by the live geofence gate (inside && not mock).
     *
     * Called by the Home holder after `CameraCaptureScreen.onSelfieReady`.
     */
    fun onSelfieReady(selfieUrl: String) {
        val ready = uiState.value as? HomeUiState.Ready ?: return
        val fix = lastFix
        val lat = fix?.lat ?: 0.0
        val lng = fix?.lng ?: 0.0
        val valid = ready.isInsideGeofence
        when (ready.action) {
            is HomeAction.CheckInPembiasaan -> onSubmitPembiasaan(lat, lng, selfieUrl, valid)
            else -> Unit // no active check-in to attach the selfie to
        }
    }


    /** Fase 2 check-in for the student's rotated activity. Location/selfie (`lat`,
     * `lng`, `selfieUrl`, `valid`) come from the geofence + CameraX flow and are
     * passed in here; the ViewModel owns the display time and delegates persistence. */
    fun onSubmitPembiasaan(lat: Double, lng: Double, selfieUrl: String, valid: Boolean) {
        val ready = uiState.value as? HomeUiState.Ready ?: return
        val activity = ready.activeActivity ?: return
        submit {
            attendanceRepository.submitPembiasaan(
                uid = ready.user.uid,
                date = todayIso(),
                grade = ready.user.grade,
                stamp = PembiasaanStamp(
                    activity = activity.toWireValue(),
                    checkedIn = true,
                    time = nowHourMinute(),
                    lat = lat,
                    lng = lng,
                    selfieUrl = selfieUrl,
                    valid = valid,
                ),
            )
        }
    }

    /** Fase 3 mandatory check-out. No geofence/selfie — only the time is recorded. */
    fun onCheckOut() {
        val uid = authRepository.currentUid ?: return
        submit {
            attendanceRepository.submitCheckout(
                uid = uid,
                date = todayIso(),
                stamp = CheckoutStamp(checkedOut = true, time = nowHourMinute()),
            )
        }
    }

    /** Acknowledge a shown success/error so it is not re-displayed on recomposition. */
    fun onSubmitStatusConsumed() {
        _submitStatus.value = SubmitStatus.Idle
    }

    /**
     * The (uid, date, phase) the selfie capture needs right now, or `null` if the
     * current state offers no geofenced check-in. Lets the camera route build its
     * Cloudinary path without the Composable re-deriving phase logic (SKILL.md §3.3).
     */
    fun currentCheckInContext(): CheckInContext? {
        val ready = uiState.value as? HomeUiState.Ready ?: return null
        val phase = when (ready.action) {
            is HomeAction.CheckInPembiasaan -> SelfiePhase.PEMBIASAAN
            else -> return null
        }
        return CheckInContext(uid = ready.user.uid, date = todayIso(), phase = phase)
    }

    fun signOut() = authRepository.signOut()

    // --- Internals --------------------------------------------------------

    private fun buildState(
        phase: FridayPhase,
        user: User,
        record: AttendanceRecord?,
        geofences: List<Geofence>,
        rotation: RotationSchedule?,
        fix: LocationFix?,
    ): HomeUiState {
        // Fake-GPS is fail-closed AND loud (SKILL.md §8, task 4.2): a mock fix can
        // never become a valid check-in, so the screen goes to Error instead of
        // silently showing a disabled button. Error renders no action buttons, so
        // every check-in path is locked while mocking is on. The fix is also not
        // cached, so an in-flight selfie cannot stamp mocked coordinates.
        if (fix != null && fix.isMock) {
            lastFix = null
            return HomeUiState.Error(R.string.location_mock_detected)
        }
        lastFix = fix
        // Firestore override wins for this grade; otherwise the pure cyclic formula.
        // Keeping the two apart lets the UI badge the override case (3.3).
        val override = rotationOverrideFor(user.grade, rotation)
        val activeActivity = override ?: cyclicActivityFor(user.grade)
        val target = resolveGeofenceTarget(phase, activeActivity, geofences)
        val distance = if (target != null && fix != null) {
            distanceMeters(fix.lat, fix.lng, target.lat, target.lng)
        } else {
            null
        }
        // `fix` is non-mock by construction here (see the guard above).
        val inside = target != null && fix != null &&
            isInsideGeofence(fix.lat, fix.lng, target.lat, target.lng, target.radiusMeter)
        return HomeUiState.Ready(
            user = user,
            phase = phase,
            activeActivity = activeActivity,
            isSpecialWeek = override != null,
            record = record,
            action = resolveHomeAction(phase, activeActivity, record, user.roleEnum),
            geofenceTarget = target,
            distanceMeters = distance,
            isInsideGeofence = inside,
            userLat = fix?.lat,
            userLng = fix?.lng,
        )
    }

    /**
     * The valid Firestore override activity for [grade], or `null` when there is
     * no override, no entry for this grade, or the wire value is unparseable
     * (`activityFromWire` rejects anything outside the three pembiasaan values —
     * including `"apel"` and typos). A `null` here means "use the formula".
     */
    private fun rotationOverrideFor(grade: Int, rotation: RotationSchedule?): Activity? {
        if (grade !in SUPPORTED_GRADES) return null
        return rotation?.mapping?.get(grade.toString())?.let(::activityFromWire)
    }

    /** The pure cyclic activity for [grade] this week, or `null` if out of range. */
    private fun cyclicActivityFor(grade: Int): Activity? =
        if (grade in SUPPORTED_GRADES) activityForGrade(grade, timeProvider.weekOfYear()) else null

    /** Runs a write, surfacing progress/outcome on [submitStatus]; never swallows failures (§5). */
    private fun submit(block: suspend () -> Result<Unit>) {
        _submitStatus.value = SubmitStatus.Submitting
        viewModelScope.launch {
            _submitStatus.value = block().fold(
                onSuccess = { SubmitStatus.Success },
                onFailure = { SubmitStatus.Error(R.string.submit_error_generic) },
            )
        }
    }

    private fun todayIso(): String = timeProvider.today().toString()

    private fun nowHourMinute(): String = timeProvider.now().format(HHMM)

    companion object {
        /** Poll cadence for the phase ticker; bounds transition lag to ≤ this. */
        private const val PHASE_TICK_MS = 30_000L

        /** Poll cadence for the location fix feeding distance + the geofence gate. */
        private const val LOCATION_POLL_MS = 5_000L

        /** Keep upstream flows warm briefly across config changes. */
        private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

        private val SUPPORTED_GRADES = 10..12
        private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        /**
         * Factory for `viewModel(factory = HomeViewModel.factory(...))`. Defaults to
         * Firebase-backed production dependencies; tests pass fakes. [locationProvider]
         * touches the Android framework so it is created at the Activity/Composable
         * boundary and injected here — never held as a `Context` in the ViewModel.
         */
        fun factory(
            locationProvider: LocationProvider,
            timeProvider: TimeProvider = SystemTimeProvider(),
            authRepository: AuthRepository = FirebaseAuthRepository(),
            attendanceRepository: AttendanceRepository = FirestoreAttendanceRepository(),
            geofenceRepository: GeofenceRepository = FirestoreGeofenceRepository(),
            rotationRepository: RotationRepository = FirestoreRotationRepository(),
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    timeProvider,
                    authRepository,
                    attendanceRepository,
                    geofenceRepository,
                    rotationRepository,
                    locationProvider,
                )
            }
        }
    }
}
