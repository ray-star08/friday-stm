package com.gynda.fridaystm.viewmodel

import com.gynda.fridaystm.R
import com.gynda.fridaystm.data.model.LarkamRun
import com.gynda.fridaystm.data.model.User
import com.gynda.fridaystm.data.repository.LarkamRepository
import com.gynda.fridaystm.util.LocationFix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

/**
 * Unit tests for [LarkamViewModel]: polling accumulates distance across fixes,
 * mock fixes are dropped, and stop persists the run to [LarkamRepository].
 * Virtual time drives both the poll loop ([advanceTimeBy]) and the elapsed clock
 * (the [FakeTimeProvider]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LarkamViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val start = LocalDateTime.of(2026, 8, 14, 6, 45)
    private val pollMs = 1_000L

    private val student = User(uid = "u1", nama = "Budi", grade = 11, kelas = "XI RPL 1", role = "student")

    private class FakeLarkamRepository(
        private val result: Result<Unit> = Result.success(Unit),
    ) : LarkamRepository {
        val logged = mutableListOf<LarkamRun>()
        override suspend fun logRun(run: LarkamRun): Result<Unit> {
            logged += run
            return result
        }
    }

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun viewModel(
        time: FakeTimeProvider,
        location: FakeLocationProvider,
        repo: FakeLarkamRepository,
    ) = LarkamViewModel(
        timeProvider = time,
        authRepository = FakeAuthRepository(profiles = mapOf(student.uid to student), initialUid = student.uid),
        locationProvider = location,
        larkamRepository = repo,
        pollIntervalMs = pollMs,
        elapsedTimeSource = com.gynda.fridaystm.util.ElapsedTimeSource { java.time.Duration.between(start, time.now()).toMillis() },
    )

    @Test fun `polling accumulates distance and drops mock fixes`() = runTest(dispatcher) {
        val time = FakeTimeProvider(start)
        val location = FakeLocationProvider(LocationFix(-6.870, 107.540, isMock = false))
        val vm = viewModel(time, location, FakeLarkamRepository())

        vm.onStart()
        runCurrent() // first tick: seeds path with point A, distance 0

        assertEquals(RunStatus.Running, vm.uiState.value.status)
        assertEquals(1, vm.uiState.value.path.size)
        assertEquals(0.0, vm.uiState.value.distanceMeters, 0.001)

        // Move ~110m and advance one poll → distance grows.
        location.set(LocationFix(-6.871, 107.540, isMock = false))
        advanceTimeBy(pollMs)
        runCurrent()
        assertEquals(2, vm.uiState.value.path.size)
        assertTrue(vm.uiState.value.distanceMeters > 50.0)

        // A mock fix must be ignored (fake-GPS, SKILL.md §8).
        val beforeMock = vm.uiState.value.distanceMeters
        location.set(LocationFix(-6.900, 107.600, isMock = true))
        advanceTimeBy(pollMs)
        runCurrent()
        assertEquals(2, vm.uiState.value.path.size)
        assertEquals(beforeMock, vm.uiState.value.distanceMeters, 0.001)

        vm.onStop() // cancel the poll loop so runTest can drain to idle
        runCurrent()
    }

    @Test fun `stop persists the run to the repository`() = runTest(dispatcher) {
        val time = FakeTimeProvider(start)
        val location = FakeLocationProvider(LocationFix(-6.870, 107.540, isMock = false))
        val repo = FakeLarkamRepository()
        val vm = viewModel(time, location, repo)

        vm.onStart()
        runCurrent()

        // Move ~110m and let 5 minutes pass on the clock, then stop.
        location.set(LocationFix(-6.871, 107.540, isMock = false))
        time.set(start.plusMinutes(5))
        advanceTimeBy(pollMs)
        runCurrent()
        vm.onStop()
        runCurrent()

        assertEquals(RunStatus.Saved, vm.uiState.value.status)
        assertEquals(1, repo.logged.size)
        val run = repo.logged.single()
        assertEquals("u1", run.userId)
        assertEquals(300L, run.elapsedSec)
        assertTrue(run.distanceMeters > 50.0)
        assertEquals(2, run.path.size)
        assertEquals(-6.870, run.path.first()["lat"]!!, 0.0001)
        assertEquals(107.540, run.path.first()["lng"]!!, 0.0001)
    }

    @Test fun `onStart is a no-op while a run is already in progress`() = runTest(dispatcher) {
        val location = FakeLocationProvider(LocationFix(-6.870, 107.540, isMock = false))
        val vm = viewModel(FakeTimeProvider(start), location, FakeLarkamRepository())

        vm.onStart()
        runCurrent()
        location.set(LocationFix(-6.871, 107.540, isMock = false))
        advanceTimeBy(pollMs)
        runCurrent()
        val pathSize = vm.uiState.value.path.size
        val distance = vm.uiState.value.distanceMeters
        assertEquals(2, pathSize)

        // A second start must not reset the accumulated run back to a fresh state.
        vm.onStart()
        runCurrent()
        assertEquals(RunStatus.Running, vm.uiState.value.status)
        assertEquals(pathSize, vm.uiState.value.path.size)
        assertEquals(distance, vm.uiState.value.distanceMeters, 0.001)

        vm.onStop()
        runCurrent()
    }

    @Test fun `onStop is a no-op when no run is running`() = runTest(dispatcher) {
        val repo = FakeLarkamRepository()
        val vm = viewModel(FakeTimeProvider(start), FakeLocationProvider(LocationFix(-6.870, 107.540, isMock = false)), repo)

        vm.onStop() // still Idle → guarded, nothing persisted
        runCurrent()

        assertEquals(RunStatus.Idle, vm.uiState.value.status)
        assertTrue(repo.logged.isEmpty())
    }

    @Test fun `a null fix still advances the clock but adds no distance`() = runTest(dispatcher) {
        val time = FakeTimeProvider(start)
        val vm = viewModel(time, FakeLocationProvider(), FakeLarkamRepository()) // provider fails → no fix

        vm.onStart()
        runCurrent()
        time.set(start.plusSeconds(10))
        advanceTimeBy(pollMs)
        runCurrent()

        val s = vm.uiState.value
        assertEquals(RunStatus.Running, s.status)
        assertEquals(10L, s.elapsedSec) // clock advanced
        assertEquals(0.0, s.distanceMeters, 0.001) // but no point to measure from
        assertTrue(s.path.isEmpty())

        vm.onStop()
        runCurrent()
    }
    @Test fun `stop without a signed-in user reports an error and writes nothing`() = runTest(dispatcher) {
        val repo = FakeLarkamRepository()
        val vm = LarkamViewModel(
            timeProvider = FakeTimeProvider(start),
            authRepository = FakeAuthRepository(profiles = emptyMap(), initialUid = null),
            locationProvider = FakeLocationProvider(LocationFix(-6.870, 107.540, isMock = false)),
            larkamRepository = repo,
            pollIntervalMs = pollMs,
        )

        vm.onStart()
        runCurrent()
        vm.onStop()
        runCurrent()

        assertEquals(RunStatus.Error(R.string.submit_error_generic), vm.uiState.value.status)
        assertTrue("no user → no write", repo.logged.isEmpty())
    }

    @Test fun `a repository failure surfaces an error status`() = runTest(dispatcher) {
        val repo = FakeLarkamRepository(Result.failure(RuntimeException("boom")))
        val vm = viewModel(FakeTimeProvider(start), FakeLocationProvider(LocationFix(-6.870, 107.540, isMock = false)), repo)

        vm.onStart()
        runCurrent()
        vm.onStop()
        runCurrent()

        assertEquals(RunStatus.Error(R.string.submit_error_generic), vm.uiState.value.status)
        assertEquals(1, repo.logged.size) // it tried; the write itself failed
    }

    @Test fun `initial state is Idle`() {
        val vm = viewModel(FakeTimeProvider(start), FakeLocationProvider(LocationFix(-6.870, 107.540, isMock = false)), FakeLarkamRepository())

        val s = vm.uiState.value
        assertEquals(RunStatus.Idle, s.status)
        assertEquals(0L, s.elapsedSec)
        assertEquals(0.0, s.distanceMeters, 0.001)
        assertTrue(s.path.isEmpty())
    }
}
