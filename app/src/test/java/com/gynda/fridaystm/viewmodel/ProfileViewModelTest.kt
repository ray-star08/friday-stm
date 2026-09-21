package com.gynda.fridaystm.viewmodel

import com.gynda.fridaystm.data.model.ProfileStats
import com.gynda.fridaystm.data.model.User
import com.gynda.fridaystm.data.repository.ProfileStatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit test [ProfileViewModel] di atas fake murni-JVM (SKILL.md §9):
 * tanpa Android framework, tanpa Firebase.
 *
 * ([FakeAuthRepository] dan [FakeTimeProvider] dipakai ulang dari
 * `TestDoubles.kt` di paket yang sama.)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val fakeUser = User(
        uid = "user123",
        nis = "2024001",
        nama = "Budi",
        grade = 11,
        kelas = "XI RPL 1",
        role = "student",
    )
    private val fakeEmail = "budi@smkn1cimahi.sch.id"
    private val fakeStats = ProfileStats(
        presensiCount = 12,
        larkamDistanceKm = 8.4,
        izinCount = 2,
    )

    /** In-memory [ProfileStatsRepository] yang mengembalikan statistik tetap. */
    private class FakeStatsRepository(
        private val stats: ProfileStats = ProfileStats(),
    ) : ProfileStatsRepository {
        var lastUserId: String? = null
        override suspend fun getStats(userId: String): Result<ProfileStats> {
            lastUserId = userId
            return Result.success(stats)
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        auth: FakeAuthRepository,
        stats: ProfileStatsRepository = FakeStatsRepository(fakeStats),
    ): ProfileViewModel = ProfileViewModel(
        authRepository = auth,
        statsRepository = stats,
    )

    private fun signedInAuth() = FakeAuthRepository(
        profiles = mapOf(fakeUser.uid to fakeUser),
        initialUid = fakeUser.uid,
        email = fakeEmail,
    )

    @Test
    fun fetchProfile_success_returnsUserProfileAndStats() = runTest(dispatcher) {
        val auth = signedInAuth()
        val statsRepo = FakeStatsRepository(fakeStats)
        val vm = viewModel(auth, statsRepo)
        val job = launch { vm.uiState.collect() }
        runCurrent()

        val state = vm.uiState.value
        assertTrue(state is UserProfileUiState.Success)
        state as UserProfileUiState.Success
        assertEquals(fakeUser, state.user)
        assertEquals(fakeEmail, state.email)
        assertEquals(fakeStats, state.stats)
        assertEquals(fakeUser.uid, statsRepo.lastUserId)

        job.cancel()
    }

    @Test
    fun logout_clearsUserSession_andTriggersNavToLogin() = runTest(dispatcher) {
        val auth = signedInAuth()
        val vm = viewModel(auth)
        val job = launch { vm.uiState.collect() }
        val logoutJob = launch { vm.logoutEvent.collect() }
        runCurrent()

        // Pre-kondisi: sesi aktif, belum ada event logout.
        assertEquals(fakeUser.uid, auth.currentUid)
        assertFalse(vm.logoutEvent.value)

        vm.logout()
        runCurrent()

        // Sesi dibersihkan (AuthState → null) dan event navigasi ke Login terpancar.
        assertNull(auth.currentUid)
        assertTrue(vm.logoutEvent.value)

        // Konsumsi event — navigasi hanya terjadi sekali.
        vm.onLogoutConsumed()
        assertFalse(vm.logoutEvent.value)

        logoutJob.cancel()
        job.cancel()
    }

    @Test
    fun fetchProfile_signedOut_returnsError() = runTest(dispatcher) {
        val auth = FakeAuthRepository(profiles = emptyMap(), initialUid = null)
        val vm = viewModel(auth)
        val job = launch { vm.uiState.collect() }
        runCurrent()

        val state = vm.uiState.value
        assertTrue(state is UserProfileUiState.Error)

        job.cancel()
    }

    @Test
    fun fetchProfile_missingProfile_returnsError() = runTest(dispatcher) {
        // UID asing tanpa dokumen profil di `users/{uid}`.
        val auth = FakeAuthRepository(profiles = emptyMap(), initialUid = "ghost")
        val vm = viewModel(auth)
        val job = launch { vm.uiState.collect() }
        runCurrent()

        val state = vm.uiState.value
        assertTrue(state is UserProfileUiState.Error)
        assertEquals(
            ProfileViewModel.MSG_LOAD_FAILED,
            (state as UserProfileUiState.Error).message,
        )

        job.cancel()
    }
}
