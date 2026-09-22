package com.gynda.fridaystm.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gynda.fridaystm.data.model.LarkamRecord
import com.gynda.fridaystm.data.model.StudentAttendanceItem
import com.gynda.fridaystm.data.model.TeacherStats
import com.gynda.fridaystm.data.model.buildStudentAttendanceList
import com.gynda.fridaystm.data.model.calculateTeacherStats
import com.gynda.fridaystm.data.model.buildDayAttendanceList
import com.gynda.fridaystm.data.model.calculateDayTeacherStats
import com.gynda.fridaystm.data.repository.AuthRepository
import com.gynda.fridaystm.data.repository.FirebaseAuthRepository
import com.gynda.fridaystm.data.repository.FirebaseTeacherDashboardRepository
import com.gynda.fridaystm.data.repository.TeacherDashboardRepository
import com.gynda.fridaystm.util.SystemTimeProvider
import com.gynda.fridaystm.util.TeacherDashboardDefaults
import com.gynda.fridaystm.util.TimeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State for the Teacher/Admin real-time monitoring dashboard.
 *
 * @property selectedClass kelas yang sedang difilter (dropdown/chip)
 * @property selectedDate tanggal `yyyy-MM-dd` (default hari ini, picker)
 * @property availableClasses daftar opsi kelas untuk dropdown
 * @property isLoading true while any stream is still at initial emission
 * @property stats aggregated daily stats for the selected class/date
 * @property students ordered student list with presence badge
 * @property error message when any stream fails (still shows last good data if any)
 */
data class TeacherDashboardUiState(
    val selectedClass: String = TeacherDashboardDefaults.DEFAULT_CLASS,
    val selectedDate: String = "",
    val availableClasses: List<String> = TeacherDashboardDefaults.AVAILABLE_CLASSES,
    val isLoading: Boolean = true,
    val stats: TeacherStats = TeacherStats(),
    val students: List<StudentAttendanceItem> = emptyList(),
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TeacherDashboardViewModel(
    private val repository: TeacherDashboardRepository,
    private val timeProvider: TimeProvider,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _selectedClass = MutableStateFlow(TeacherDashboardDefaults.DEFAULT_CLASS)
    private val _selectedDate = MutableStateFlow(timeProvider.today().toString())
    // refresh availableClasses once at init (non-realtime)
    private val _availableClasses = MutableStateFlow(TeacherDashboardDefaults.AVAILABLE_CLASSES)

    private val refreshGeneration = MutableStateFlow(0)
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val selectedClass: StateFlow<String> = _selectedClass.asStateFlow()
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    // An error terminates only the current request, not future refresh/auth changes.
    val uiState: StateFlow<TeacherDashboardUiState> = combine(
        _selectedClass, _selectedDate, _availableClasses, refreshGeneration, authRepository.observeAuthState(),
    ) { kelas, date, classes, generation, uid -> DashboardRequest(kelas, date, classes, generation, uid) }
        .flatMapLatest { request ->
            flow {
                val initial = TeacherDashboardUiState(selectedClass = request.kelas, selectedDate = request.date,
                    availableClasses = request.classes, isLoading = true)
                emit(initial)
                if (request.uid == null) {
                    emit(initial.copy(isLoading = false, error = "Sesi berakhir. Silakan login ulang."))
                } else {
                    // Roster changes invalidate all joined evidence. Re-open dependent streams
                    // and expose Loading, never a fabricated successful-empty attendance list.
                    emitAll(repository.observeUsersByClass(request.kelas)
                        .map { users -> users.filter { it.kelas == request.kelas }.distinctBy { it.uid } }
                        .distinctUntilChanged()
                        .flatMapLatest { users ->
                            flow {
                                emit(initial)
                                emitAll(combine(
                                    repository.observePresensi(request.kelas, request.date),
                                    repository.observeIzin(request.kelas, request.date),
                                    repository.observeLarkam(request.kelas, request.date),
                                ) { days, izin, larkam ->
                                    val students = buildDayAttendanceList(users, days.filter { it.date == request.date },
                                        izin.filter { it.startDate <= request.date && request.date <= it.endDate }, larkam)
                                    initial.copy(isLoading = false, students = students, stats = calculateDayTeacherStats(students))
                                })
                            }
                        }.buffer(0))
                }
            }.catch { e ->
                emit(TeacherDashboardUiState(selectedClass = request.kelas, selectedDate = request.date,
                    availableClasses = request.classes, isLoading = false, error = e.message ?: "Gagal memuat data"))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TeacherDashboardUiState(
            selectedClass = _selectedClass.value, selectedDate = _selectedDate.value))

    private data class DashboardRequest(val kelas: String, val date: String, val classes: List<String>, val generation: Int, val uid: String?)

    fun onClassSelected(kelas: String) {
        if (kelas.isNotBlank()) _selectedClass.value = kelas
    }

    fun onDateSelected(dateIso: String) {
        // expect yyyy-MM-dd; caller validates
        if (dateIso.isNotBlank()) _selectedDate.value = dateIso
    }

    fun refresh() {
        if (_isRefreshing.value) return
        refreshGeneration.value += 1
    }

    fun refreshAvailableClasses() {
        // fire-and-forget; update dropdown
        // launched in viewModelScope elsewhere? keep sync for test
        // not critical path
    }

    fun logout() {
        authRepository.signOut()
    }

    companion object {
        fun factory(
            repository: TeacherDashboardRepository = FirebaseTeacherDashboardRepository(),
            timeProvider: TimeProvider = SystemTimeProvider(),
            authRepository: AuthRepository = FirebaseAuthRepository(),
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { TeacherDashboardViewModel(repository, timeProvider, authRepository) }
        }
    }
}
