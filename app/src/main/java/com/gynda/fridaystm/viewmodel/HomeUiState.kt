package com.gynda.fridaystm.viewmodel

import androidx.annotation.StringRes
import com.gynda.fridaystm.data.model.AttendanceRecord
import com.gynda.fridaystm.data.model.Geofence
import com.gynda.fridaystm.data.model.User
import com.gynda.fridaystm.data.model.activityFromWire
import com.gynda.fridaystm.data.model.toWireValue
import com.gynda.fridaystm.domain.Activity
import com.gynda.fridaystm.domain.FridayPhase
import com.gynda.fridaystm.domain.Role

/**
 * The single, exhaustive UI state for the Home screen (SKILL.md §3.2).
 *
 * Produced by `HomeViewModel` by combining the phase ticker, the signed-in
 * profile, the weekly rotation and today's attendance record. The Composable
 * only renders from this — no business logic in the UI layer (SKILL.md §3.3).
 */
sealed interface HomeUiState {

    /** Still resolving auth/profile — show a spinner. */
    data object Loading : HomeUiState

    /** No authenticated user — the holder should route to Login. */
    data object SignedOut : HomeUiState

    /**
     * The normal, phase-driven state.
     *
     * @property activeActivity the pembiasaan activity this student's grade is
     *   routed to this week (`null` if grade is unknown/out of range).
     * @property isSpecialWeek `true` when [activeActivity] came from a Firestore
     *   rotation **override** for this grade (a holiday/special-week schedule),
     *   rather than the cyclic formula — drives the "Jadwal Khusus" badge (3.3).
     * @property record today's attendance so far (`null` before any check-in).
     * @property action the single primary action the UI should surface now.
     * @property geofenceTarget the fence the current phase requires the student to
     *   be inside (`null` for check-out / non-geofenced phases).
     * @property distanceMeters live distance to [geofenceTarget]'s center, or
     *   `null` while no fix / no target — drives the "Anda N m dari …" hint.
     * @property isInsideGeofence whether the latest fix is within the target
     *   radius; gates the check-in button (Milestone 4.2). Always `false` when a
     *   geofenced phase has no fix yet.
     * @property userLat latest device latitude, or `null` before any fix — feeds
     *   the mini-map marker (Milestone 5.1).
     * @property userLng latest device longitude; see [userLat].
     */
    data class Ready(
        val user: User,
        val phase: FridayPhase,
        val activeActivity: Activity?,
        val isSpecialWeek: Boolean = false,
        val record: AttendanceRecord?,
        val action: HomeAction,
        val geofenceTarget: Geofence? = null,
        val distanceMeters: Double? = null,
        val isInsideGeofence: Boolean = false,
        val userLat: Double? = null,
        val userLng: Double? = null,
    ) : HomeUiState

    /** A load failure; [messageResId] is resolved to text in the Composable. */
    data class Error(@StringRes val messageResId: Int) : HomeUiState
}

/**
 * The single primary action the Home screen offers right now, derived purely
 * from phase + rotation + record by [resolveHomeAction].
 *
 * Note: whether a check-in button is *enabled* additionally depends on the
 * geofence radius check — that gating is wired in Milestone 4; here we only
 * decide *which* action is relevant for the current phase.
 */
sealed interface HomeAction {
    /** Nothing to do (before/after the flow, non-Friday, or unknown grade). */
    data object None : HomeAction

    data class CheckInPembiasaan(val activity: Activity) : HomeAction
    data class PembiasaanDone(val activity: Activity) : HomeAction

    data object CheckOut : HomeAction
    data object CheckedOut : HomeAction
}

/**
 * Transient outcome of a check-in/check-out write, exposed by the ViewModel as a
 * separate small flow so a one-off success/error (snackbar) does not pollute the
 * long-lived [HomeUiState].
 */
sealed interface SubmitStatus {
    data object Idle : SubmitStatus
    data object Submitting : SubmitStatus
    data object Success : SubmitStatus
    data class Error(@StringRes val messageResId: Int) : SubmitStatus
}

/**
 * Everything the selfie capture route needs to build its Cloudinary path and,
 * on return, attach the resulting URL to the right attendance write.
 *
 * @property phase one of [com.gynda.fridaystm.util.SelfiePhase].
 */
data class CheckInContext(
    val uid: String,
    val date: String,
    val phase: String,
)

/**
 * Pure mapping of (phase, activity, record, role) → [HomeAction].
 *
 * Kept out of the Composable (SKILL.md §3.3) and free of I/O so it is trivially
 * unit-testable. `internal` so ViewModel tests can drive it directly.
 *
 * Role gating: only [Role.canAttendPembiasaan] roles are offered a Pembiasaan
 * check-in. Instructors don't check in (they manage the Senam video), so they get
 * [HomeAction.None] during Pembiasaan.
 */
internal fun resolveHomeAction(
    phase: FridayPhase,
    activeActivity: Activity?,
    record: AttendanceRecord?,
    role: Role,
): HomeAction = when (phase) {
    FridayPhase.PEMBIASAAN -> {
        val alreadyCheckedIn = record?.pembiasaan?.checkedIn == true
        // Prefer the activity actually recorded; fall back to today's rotation.
        val doneActivity = record?.pembiasaan?.activity?.let(::activityFromWire) ?: activeActivity
        when {
            alreadyCheckedIn && doneActivity != null -> HomeAction.PembiasaanDone(doneActivity)
            !role.canAttendPembiasaan -> HomeAction.None
            activeActivity != null -> HomeAction.CheckInPembiasaan(activeActivity)
            else -> HomeAction.None
        }
    }

    FridayPhase.CHECKOUT ->
        if (!role.canAttendPembiasaan) HomeAction.None
        else if (record?.checkout?.checkedOut == true) HomeAction.CheckedOut else HomeAction.CheckOut

    FridayPhase.NOT_FRIDAY,
    FridayPhase.BEFORE,
    FridayPhase.DONE,
    -> HomeAction.None
}

/**
 * Pure selection of the geofence the current phase requires, from the loaded
 * reference list. `null` when no fence applies (check-out or the matching fence
 * isn't seeded yet). Kept out of the Composable and I/O-free so it is
 * unit-testable (SKILL.md §6).
 *
 * - [FridayPhase.PEMBIASAAN] → the fence whose `activity` matches [activeActivity].
 * - everything else → `null` (check-out has no geofence, per task.md).
 */
internal fun resolveGeofenceTarget(
    phase: FridayPhase,
    activeActivity: Activity?,
    geofences: List<Geofence>,
): Geofence? {
    val wanted: String = when (phase) {
        FridayPhase.PEMBIASAAN -> activeActivity?.toWireValue() ?: return null
        else -> return null
    }
    return geofences.firstOrNull { it.activity == wanted }
}
