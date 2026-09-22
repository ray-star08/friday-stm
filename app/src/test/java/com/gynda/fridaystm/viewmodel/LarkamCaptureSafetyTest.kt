package com.gynda.fridaystm.viewmodel

import androidx.lifecycle.ViewModelStore
import com.gynda.fridaystm.data.model.LarkamRun
import com.gynda.fridaystm.data.model.User
import com.gynda.fridaystm.data.repository.LarkamRepository
import com.gynda.fridaystm.util.LocationFix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class LarkamCaptureSafetyTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()
    private val friday = LocalDateTime.of(2026, 8, 14, 7, 0)
    private val student = User(uid = "student-a", role = "student")

    private class Runs : LarkamRepository {
        val logged = mutableListOf<LarkamRun>()
        override suspend fun logRun(run: LarkamRun): Result<Unit> {
            logged += run
            return Result.success(Unit)
        }
    }

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() {
        stores.forEach { it.clear() }
        Dispatchers.resetMain()
    }

    private fun tracker(
        time: FakeTimeProvider = FakeTimeProvider(friday),
        auth: FakeAuthRepository = FakeAuthRepository(mapOf(student.uid to student), student.uid),
        runs: Runs = Runs(),
    ): LarkamViewModel = LarkamViewModel(
        time, auth, FakeLocationProvider(LocationFix(-6.87, 107.54, false)), runs,
        pollIntervalMs = 1_000,
        elapsedTimeSource = com.gynda.fridaystm.util.ElapsedTimeSource { java.time.Duration.between(friday, time.now()).toMillis() },
    ).also { vm -> stores += ViewModelStore().also { it.put("tracker", vm) } }

    private fun safetyTest(block: suspend kotlinx.coroutines.test.TestScope.() -> Unit) = runTest(dispatcher) {
        try { block() } finally { stores.forEach { it.clear() } }
    }

    @Test fun `debug factory stopwatch advances independently of simulated phase and excludes pauses`() = safetyTest {
        val debug = com.gynda.fridaystm.util.DebugTimeProvider(friday)
        var elapsedMillis = 1_000L
        val elapsed = com.gynda.fridaystm.util.ElapsedTimeSource { elapsedMillis }
        val auth = FakeAuthRepository(mapOf(student.uid to student), student.uid)
        val vm = LarkamViewModel.factory(
            FakeLocationProvider(LocationFix(-6.87, 107.54, false)), debug, auth, Runs(), elapsed,
        ).create(LarkamViewModel::class.java, androidx.lifecycle.viewmodel.CreationExtras.Empty)
        stores += ViewModelStore().also { it.put("debug-stopwatch", vm) }
        vm.onStart()
        runCurrent()
        elapsedMillis += 61_000
        vm.onPause()
        assertEquals(61L, vm.uiState.value.elapsedSec)
        elapsedMillis += 100_000
        debug.setFridayPhase(8, 1)
        vm.onResume()
        runCurrent()
        elapsedMillis += 9_000
        vm.onFinish()
        assertEquals(70L, requireNotNull(vm.captureIntent()).run.durationSeconds)
        assertEquals("00:01:10", vm.uiState.value.timerFormatted)
    }

    @Test fun `cancelled location result cannot mutate a finished run`() = safetyTest {
        val time = FakeTimeProvider(friday)
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val location = object : com.gynda.fridaystm.util.LocationProvider {
            override suspend fun currentLocation(): Result<LocationFix> = try {
                gate.await()
                Result.success(LocationFix(-6.87, 107.54, false))
            } catch (_: kotlinx.coroutines.CancellationException) {
                // Match a wrapped SDK await that returns cancellation as Result.failure.
                Result.success(LocationFix(-6.88, 107.55, false))
            }
        }
        val auth = FakeAuthRepository(mapOf(student.uid to student), student.uid)
        val vm = LarkamViewModel(time, auth, location, Runs(), pollIntervalMs = 1_000)
        stores += ViewModelStore().also { it.put("pending-location", vm) }
        vm.onStart()
        runCurrent()
        time.set(friday.plusSeconds(10))
        vm.onFinish()
        val finished = vm.uiState.value
        runCurrent()
        assertEquals("a late cancelled GPS response cannot change frozen evidence", finished, vm.uiState.value)
    }

    @Test fun `finished selfie intent freezes exact duration owner and route without reporting persistence`() = safetyTest {
        val time = FakeTimeProvider(friday)
        val auth = FakeAuthRepository(mapOf(student.uid to student), student.uid)
        val runs = Runs()
        val vm = tracker(time, auth, runs)
        assertNull(vm.captureIntent())
        vm.onStart()
        runCurrent()
        time.set(friday.plusSeconds(61))
        vm.onPause()
        time.set(friday.plusSeconds(161))
        vm.onFinish()
        val intent = requireNotNull(vm.captureIntent())
        assertEquals(61L, intent.run.durationSeconds)
        assertEquals(student.uid, intent.ownerUid)
        assertEquals(friday.toLocalDate(), intent.date)
        assertEquals(listOf(mapOf("lat" to -6.87, "lng" to 107.54)), intent.run.route)
        time.set(friday.plusMinutes(10))
        assertEquals(intent, vm.captureIntent())
        assertTrue(runs.logged.isEmpty())
        assertEquals(RunStatus.Finished, vm.uiState.value.status)
        auth.emitAuthState("other")
        assertNull(vm.captureIntent())
    }

    @Test fun `finish rejects stale school day and permits checkout completion`() = safetyTest {
        val time = FakeTimeProvider(friday.withHour(7).withMinute(59))
        val vm = tracker(time)
        vm.onStart()
        runCurrent()
        time.set(friday.withHour(8).withMinute(1))
        vm.onFinish()
        assertEquals(120L, requireNotNull(vm.captureIntent()).run.durationSeconds)
        time.set(friday.plusDays(7))
        assertNull(vm.captureIntent())
    }

    @Test fun `account change never assigns the previous run to the new owner`() = safetyTest {
        val other = student.copy(uid = "student-b")
        val auth = FakeAuthRepository(mapOf(student.uid to student, other.uid to other), student.uid)
        val runs = Runs()
        val vm = tracker(auth = auth, runs = runs)
        vm.onStart()
        runCurrent()
        assertEquals(1, vm.uiState.value.path.size)
        auth.emitAuthState(other.uid)
        vm.onStop() // before the auth collector has a chance to run
        runCurrent()
        assertTrue("an old run must never be written for either account", runs.logged.isEmpty())
        assertTrue(vm.uiState.value.status is RunStatus.Error)
        assertTrue("account changes clear the previous route", vm.uiState.value.path.isEmpty())
    }

    @Test fun `start accepts only authenticated attendees during pembiasaan`() = safetyTest {
        val denied = listOf(
            null to friday,
            student.copy(role = "admin") to friday,
            student.copy(role = "instructor") to friday,
            student.copy(role = "unknown") to friday,
            student.copy(role = "") to friday,
            student to friday.withHour(6).withMinute(29),
            student to friday.withHour(8),
            student to friday.withHour(8).withMinute(30),
            student to friday.plusDays(1),
        )
        for ((user, now) in denied) {
            val auth = FakeAuthRepository(user?.let { mapOf(it.uid to it) }.orEmpty(), user?.uid)
            val vm = tracker(FakeTimeProvider(now), auth)
            vm.onStart()
            runCurrent()
            assertTrue("must reject uid=${user?.uid}, role=${user?.role}, time=$now", vm.uiState.value.status is RunStatus.Error)
            assertTrue(vm.uiState.value.path.isEmpty())
        }
        for (role in listOf("student", "class_rep")) {
            val user = student.copy(role = role)
            val vm = tracker(auth = FakeAuthRepository(mapOf(user.uid to user), user.uid))
            vm.onStart()
            runCurrent()
            assertEquals(RunStatus.Running, vm.uiState.value.status)
            vm.onPause()
        }
    }
}
