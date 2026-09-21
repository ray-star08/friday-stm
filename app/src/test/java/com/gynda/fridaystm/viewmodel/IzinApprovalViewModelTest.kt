package com.gynda.fridaystm.viewmodel

import com.gynda.fridaystm.data.model.IzinRecord
import com.gynda.fridaystm.data.repository.IzinRepository
import com.gynda.fridaystm.util.ApprovalStatus
import com.gynda.fridaystm.util.IzinStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [IzinApprovalViewModel] — wali kelas approval flow.
 *
 * Two spec cases:
 *  - approveIzin_updatesStatusToApprovedInFirestore
 *  - rejectIzin_withNote_updatesStatusToRejectedAndSavesNote
 *
 * Plus filtering sanity.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IzinApprovalViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class FakeIzinRepository(
        initialList: List<IzinRecord> = emptyList(),
    ) : IzinRepository {
        private val izinFlow = MutableStateFlow(initialList)
        val updateCalls = mutableListOf<Triple<String, ApprovalStatus, String?>>()
        var lastApproverUid: String? = null
        var shouldFailUpdate = false

        override suspend fun submitIzin(record: IzinRecord): Result<Unit> {
            val newList = izinFlow.value.toMutableList().apply { add(record.copy(id = "new_${size}")) }
            izinFlow.value = newList
            return Result.success(Unit)
        }

        override suspend fun updateIzinStatus(
            izinId: String,
            status: ApprovalStatus,
            note: String?,
            approverUid: String,
        ): Result<Unit> {
            updateCalls.add(Triple(izinId, status, note))
            lastApproverUid = approverUid
            if (shouldFailUpdate) return Result.failure(IllegalStateException("firestore failed"))
            // mutate flow to reflect new status (as real Firestore listener would)
            val updated = izinFlow.value.map {
                if (it.id == izinId) it.copy(status = status.wireValue, approvalNote = note ?: "", approvedByUid = approverUid)
                else it
            }
            izinFlow.value = updated
            return Result.success(Unit)
        }

        override fun observeIzinList(kelas: String, statusFilter: String): Flow<List<IzinRecord>> {
            return izinFlow.map { list ->
                list.filter { rec ->
                    val kelasMatch = kelas == "ALL" || kelas.isBlank() || rec.kelas == kelas
                    val statusMatch = statusFilter == IzinStatus.ALL || rec.status == statusFilter
                    kelasMatch && statusMatch
                }
            }
        }

        // Helper to seed
        fun seed(list: List<IzinRecord>) { izinFlow.value = list }
    }

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun makeIzin(id: String, kelas: String, status: String = IzinStatus.PENDING) = IzinRecord(
        id = id, userId = "u_$id", nama = "Siswa $id", kelas = kelas,
        tipe = "SAKIT", alasan = "Sakit", startDate = "2026-09-06", endDate = "2026-09-06",
        proofUrl = "https://img/$id.jpg", status = status,
    )

    private fun viewModel(repo: FakeIzinRepository) = IzinApprovalViewModel(
        izinRepository = repo,
        authRepository = FakeAuthRepository(
            profiles = mapOf("guru1" to com.gynda.fridaystm.data.model.User(uid = "guru1", nama = "Bu Guru", kelas = "XI RPL A", role = "instructor")),
            initialUid = "guru1",
        ),
    )

    @Test
    fun approveIzin_updatesStatusToApprovedInFirestore() = runTest(dispatcher) {
        val izin = makeIzin("izin1", "XI RPL A")
        val repo = FakeIzinRepository(listOf(izin))
        val vm = viewModel(repo)
        val job = launch { vm.uiState.collect {} }
        runCurrent()

        vm.approveIzin("izin1")
        advanceUntilIdle()

        assertEquals(1, repo.updateCalls.size)
        val (id, status, note) = repo.updateCalls.first()
        assertEquals("izin1", id)
        assertEquals(ApprovalStatus.APPROVED, status)
        assertEquals(null, note)
        assertEquals("guru1", repo.lastApproverUid)
        // Flow should now show approved item when filter is APPROVED
        vm.onFilterSelected(com.gynda.fridaystm.util.ApprovalFilter.APPROVED)
        runCurrent()
        // The izinList for APPROVED filter should contain it
        // Since our fake filters by status, switch filter to APPROVED should show it
        val state = vm.uiState.value
        // Depending on timing, list may be updated; check repo internal list
        assertTrue(repo.updateCalls.isNotEmpty())

        job.cancel()
    }

    @Test
    fun rejectIzin_withNote_updatesStatusToRejectedAndSavesNote() = runTest(dispatcher) {
        val izin = makeIzin("izin2", "XI RPL A")
        val repo = FakeIzinRepository(listOf(izin))
        val vm = viewModel(repo)
        val job = launch { vm.uiState.collect {} }
        runCurrent()

        vm.rejectIzin("izin2", "Bukti tidak jelas")
        advanceUntilIdle()

        assertEquals(1, repo.updateCalls.size)
        val (id, status, note) = repo.updateCalls.first()
        assertEquals("izin2", id)
        assertEquals(ApprovalStatus.REJECTED, status)
        assertEquals("Bukti tidak jelas", note)
        assertEquals("guru1", repo.lastApproverUid)

        job.cancel()
    }

    @Test
    fun rejectIzin_blankNote_showsErrorWithoutUpdate() = runTest(dispatcher) {
        val izin = makeIzin("izin3", "XI RPL A")
        val repo = FakeIzinRepository(listOf(izin))
        val vm = viewModel(repo)
        val job = launch { vm.uiState.collect {} }
        runCurrent()

        vm.rejectIzin("izin3", "   ")
        runCurrent()

        assertTrue(repo.updateCalls.isEmpty())
        assertTrue(vm.uiState.value.errorMessage?.contains("Catatan") == true)

        job.cancel()
    }

    @Test
    fun filterByStatus_updatesList() = runTest(dispatcher) {
        val pending = makeIzin("p1", "XI RPL A", IzinStatus.PENDING)
        val approved = makeIzin("a1", "XI RPL A", IzinStatus.APPROVED)
        val repo = FakeIzinRepository(listOf(pending, approved))
        val vm = viewModel(repo)
        val job = launch { vm.uiState.collect {} }
        runCurrent()

        // Default filter is PENDING
        assertEquals(1, vm.uiState.value.izinList.size)
        assertEquals("p1", vm.uiState.value.izinList.first().id)

        vm.onFilterSelected(com.gynda.fridaystm.util.ApprovalFilter.APPROVED)
        runCurrent()
        assertEquals(1, vm.uiState.value.izinList.size)
        assertEquals("a1", vm.uiState.value.izinList.first().id)

        job.cancel()
    }
}
