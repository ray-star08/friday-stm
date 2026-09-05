package com.gynda.fridaystm.viewmodel

import com.gynda.fridaystm.R
import com.gynda.fridaystm.data.model.TalimSummary
import com.gynda.fridaystm.data.model.User
import com.gynda.fridaystm.data.repository.TalimRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

/**
 * Unit tests for [TalimViewModel] on JVM fakes: role gating + client-side
 * validation of the class-summary submit. Time is virtualized via a shared
 * [StandardTestDispatcher] so `viewModelScope` work drains under [runCurrent].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TalimViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val friday = LocalDateTime.of(2026, 8, 14, 6, 45)

    private val classRep = User(uid = "r1", nama = "Ketua", grade = 11, kelas = "XI RPL 1", role = "class_rep")
    private val student = User(uid = "s1", nama = "Budi", grade = 11, kelas = "XI RPL 1", role = "student")

    /** In-memory [TalimRepository] capturing writes; observe streams the last write. */
    private class FakeTalimRepository : TalimRepository {
        val submitted = mutableListOf<TalimSummary>()
        private val current = MutableStateFlow<TalimSummary?>(null)
        override fun observeSummary(date: String, kelas: String): Flow<TalimSummary?> = current
        override suspend fun submitSummary(summary: TalimSummary): Result<Unit> {
            submitted += summary
            current.value = summary
            return Result.success(Unit)
        }
    }

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun viewModel(user: User, repo: FakeTalimRepository) = TalimViewModel(
        timeProvider = FakeTimeProvider(friday),
        authRepository = FakeAuthRepository(profiles = mapOf(user.uid to user), initialUid = user.uid),
        talimRepository = repo,
    )

    @Test fun `class rep can submit`() = runTest(dispatcher) {
        val repo = FakeTalimRepository()
        val vm = viewModel(classRep, repo)
        val job = launch { vm.uiState.collect {} }
        runCurrent()
        assertTrue(vm.uiState.value.canSubmit)
        assertEquals("XI RPL 1", vm.uiState.value.kelas)
        job.cancel()
    }

    @Test fun `plain student cannot submit`() = runTest(dispatcher) {
        val repo = FakeTalimRepository()
        val vm = viewModel(student, repo)
        val job = launch { vm.uiState.collect {} }
        runCurrent()
        assertFalse(vm.uiState.value.canSubmit)
        job.cancel()
    }

    @Test fun `blank fields are rejected without a write`() = runTest(dispatcher) {
        val repo = FakeTalimRepository()
        val vm = viewModel(classRep, repo)
        val job = launch { vm.uiState.collect {} }
        runCurrent()

        vm.onSubmit(penceramah = "  ", tema = "Adab", ringkasan = "isi")
        runCurrent()

        assertTrue(vm.submitStatus.value is SubmitStatus.Error)
        assertEquals(
            R.string.talim_error_incomplete,
            (vm.submitStatus.value as SubmitStatus.Error).messageResId,
        )
        assertTrue(repo.submitted.isEmpty())
        job.cancel()
    }

    @Test fun `valid submit writes stamped summary`() = runTest(dispatcher) {
        val repo = FakeTalimRepository()
        val vm = viewModel(classRep, repo)
        val job = launch { vm.uiState.collect {} }
        runCurrent()

        vm.onSubmit(penceramah = " Ust. Fulan ", tema = " Adab ", ringkasan = " ringkas ")
        runCurrent()

        assertEquals(SubmitStatus.Success, vm.submitStatus.value)
        assertEquals(1, repo.submitted.size)
        val s = repo.submitted.single()
        assertEquals("Ust. Fulan", s.penceramah)   // trimmed
        assertEquals("XI RPL 1", s.kelas)
        assertEquals("r1", s.submittedByUid)
        assertEquals("2026-08-14_XI RPL 1", s.docId)
        job.cancel()
    }
}
