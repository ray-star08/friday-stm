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
import com.gynda.fridaystm.data.model.earliestDailyPresensi
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

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val selectedClass: StateFlow<String> = _selectedClass.asStateFlow()
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    // Combine class/date into realtime streams → aggregated state.
    // Each filter change flatMapLatest-cancels the previous 4 listeners.
    val uiState: StateFlow<TeacherDashboardUiState> =
        combine(_selectedClass, _selectedDate, _availableClasses) { k, d, classes -> Triple(k, d, classes) }
            .flatMapLatest { (kelas, date, classes) ->
                combine(
                    repository.observeUsersByClass(kelas),
                    repository.observePresensi(kelas, date),
                    repository.observeIzin(kelas, date),
                    repository.observeLarkam(kelas, date),
                ) { users, presensi, izin, larkam ->
                    val presensiByUser = earliestDailyPresensi(presensi).associateBy { it.userId }
                    val izinByUser = izin.filter { it.status == com.gynda.fridaystm.util.IzinStatus.APPROVED }.associateBy { it.userId }
                    // Larkam stream returns all classes for the date; narrow to this roster.
                    val userIdsInClass = users.map { it.uid }.toSet()
                    val classLarkam = larkam.filter { it.userId in userIdsInClass }
                    val larkamByUser = classLarkam.associateBy { it.userId }

                    val stats = calculateTeacherStats(
                        totalStudents = users.size,
                        hadirUserIds = presensiByUser.keys,
                        izinUserIds = izinByUser.keys,
                        larkamRecords = classLarkam,
                    )
                    val students = buildStudentAttendanceList(
                        users = users.sortedBy { it.nama },
                        presensiByUserId = presensiByUser,
                        izinByUserId = izinByUser,
                        larkamByUserId = larkamByUser,
                    )
                    TeacherDashboardUiState(
                        selectedClass = kelas,
                        selectedDate = date,
                        availableClasses = classes,
                        isLoading = false,
                        stats = stats,
                        students = students,
                        error = null,
                    )
                }
            }
            .catch { e ->
                emit(
                    TeacherDashboardUiState(
                        selectedClass = _selectedClass.value,
                        selectedDate = _selectedDate.value,
                        availableClasses = _availableClasses.value,
                        isLoading = false,
                        error = e.message ?: "Gagal memuat data",
                    )
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = TeacherDashboardUiState(
                    selectedClass = _selectedClass.value,
                    selectedDate = _selectedDate.value,
                    availableClasses = _availableClasses.value,
                    isLoading = true,
                ),
            )

    fun onClassSelected(kelas: String) {
        if (kelas.isNotBlank()) _selectedClass.value = kelas
    }

    fun onDateSelected(dateIso: String) {
        // expect yyyy-MM-dd; caller validates
        if (dateIso.isNotBlank()) _selectedDate.value = dateIso
    }

    fun refresh() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        viewModelScope.launch {
            kotlinx.coroutines.delay(600)
            _isRefreshing.value = false
        }
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
