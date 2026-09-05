package com.gynda.fridaystm.viewmodel

import com.gynda.fridaystm.data.model.AttendanceRecord
import com.gynda.fridaystm.data.model.CheckoutStamp
import com.gynda.fridaystm.data.model.Geofence
import com.gynda.fridaystm.data.model.PembiasaanStamp
import com.gynda.fridaystm.data.model.RotationSchedule
import com.gynda.fridaystm.data.model.User
import com.gynda.fridaystm.data.repository.AttendanceRepository
import com.gynda.fridaystm.data.repository.AuthRepository
import com.gynda.fridaystm.data.repository.GeofenceRepository
import com.gynda.fridaystm.data.repository.RotationRepository
import com.gynda.fridaystm.util.LocationFix
import com.gynda.fridaystm.util.LocationProvider
import com.gynda.fridaystm.util.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDateTime

/**
 * Test doubles for the `HomeViewModel` unit tests.
 *
 * All three fakes are plain JVM classes — no Android framework, no Firebase — so
 * the ViewModel logic (phase ticking, `combine`, action derivation) runs in
 * milliseconds on the JVM (SKILL.md §9).
 */

/** A clock the test drives by hand: mutate [set]/[setWeek] to advance time. */
class FakeTimeProvider(
    now: LocalDateTime,
    week: Int = 33,
) : TimeProvider {
    private var current: LocalDateTime = now
    private var weekValue: Int = week

    override fun now(): LocalDateTime = current
    override fun weekOfYear(): Int = weekValue

    fun set(dateTime: LocalDateTime) { current = dateTime }
    fun setWeek(value: Int) { weekValue = value }
}

/**
 * In-memory [AuthRepository]. Auth state is a [MutableStateFlow] the test pushes
 * to via [emitAuthState] (simulating sign-in / sign-out); profiles come from a
 * fixed map.
 */
class FakeAuthRepository(
    private val profiles: Map<String, User> = emptyMap(),
    initialUid: String? = null,
) : AuthRepository {

    private val authState = MutableStateFlow(initialUid)

    override val currentUid: String? get() = authState.value

    override fun observeAuthState(): Flow<String?> = authState

    override fun observeUserProfile(uid: String): Flow<User?> = flowOf(profiles[uid])

    override suspend fun signIn(email: String, password: String): Result<User> =
        profiles.values.firstOrNull()?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("no fake profile configured"))

    override suspend fun getUserProfile(uid: String): Result<User> =
        profiles[uid]?.let { Result.success(it) }
            ?: Result.failure(NoSuchElementException("no fake profile for uid=$uid"))

    override fun signOut() { authState.value = null }

    /** Simulate an auth-state change (sign-in with a uid, or `null` for sign-out). */
    fun emitAuthState(uid: String?) { authState.value = uid }
}

/**
 * In-memory [AttendanceRepository]. [observeTodayRecord] is backed by a
 * [MutableStateFlow] the submit calls mutate, so a check-in immediately flows
 * back into the ViewModel state — exactly like a Firestore snapshot would.
 * Submitted stamps are captured for assertions.
 */
class FakeAttendanceRepository(
    initialRecord: AttendanceRecord? = null,
) : AttendanceRepository {

    private val record = MutableStateFlow(initialRecord)

    val pembiasaanCalls = mutableListOf<PembiasaanStamp>()
    val checkoutCalls = mutableListOf<CheckoutStamp>()

    override fun observeTodayRecord(uid: String, date: String): Flow<AttendanceRecord?> = record

    override fun observeHistory(uid: String): Flow<List<AttendanceRecord>> =
        MutableStateFlow(listOfNotNull(record.value))

    override suspend fun submitPembiasaan(uid: String, date: String, grade: Int, stamp: PembiasaanStamp): Result<Unit> {
        pembiasaanCalls += stamp
        record.value = currentOrNew(uid, date, grade).copy(pembiasaan = stamp)
        return Result.success(Unit)
    }

    override suspend fun submitCheckout(uid: String, date: String, stamp: CheckoutStamp): Result<Unit> {
        checkoutCalls += stamp
        record.value = currentOrNew(uid, date, record.value?.grade ?: 0).copy(checkout = stamp)
        return Result.success(Unit)
    }

    private fun currentOrNew(uid: String, date: String, grade: Int): AttendanceRecord =
        record.value ?: AttendanceRecord(uid = uid, date = date, grade = grade)
}

/** In-memory [GeofenceRepository] serving a fixed fence list. */
class FakeGeofenceRepository(
    private val geofences: List<Geofence> = emptyList(),
) : GeofenceRepository {
    override fun observeGeofences(): Flow<List<Geofence>> = flowOf(geofences)
}

/**
 * In-memory [RotationRepository]. Defaults to `null` — **no override**, which is
 * the production norm and makes the cyclic formula the expected path.
 *
 * [requestedWeekIds] records which week key the ViewModel asked for, so a test can
 * assert the lookup is keyed on `TimeProvider.weekId()` and not something else.
 */
class FakeRotationRepository(
    schedule: RotationSchedule? = null,
) : RotationRepository {

    private val active = MutableStateFlow(schedule)
    val requestedWeekIds = mutableListOf<String>()

    override fun observeActiveSchedule(weekId: String): Flow<RotationSchedule?> {
        requestedWeekIds += weekId
        return active
    }

    /** Publish (or with `null` retract) an override mid-test. */
    fun set(schedule: RotationSchedule?) { active.value = schedule }
}

/**
 * [LocationProvider] returning a fix the test controls via [set]. Defaults to a
 * failure (no fix) so gating stays fail-closed unless a position is provided.
 */
class FakeLocationProvider(
    fix: LocationFix? = null,
) : LocationProvider {
    private var current: Result<LocationFix> =
        fix?.let { Result.success(it) } ?: Result.failure(IllegalStateException("no fix"))

    override suspend fun currentLocation(): Result<LocationFix> = current

    fun set(fix: LocationFix) { current = Result.success(fix) }
    fun fail() { current = Result.failure(IllegalStateException("no fix")) }
}
