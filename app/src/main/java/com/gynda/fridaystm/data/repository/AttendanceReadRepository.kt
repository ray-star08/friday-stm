package com.gynda.fridaystm.data.repository

import com.gynda.fridaystm.data.model.AttendanceDay
import com.gynda.fridaystm.data.model.AttendanceRecord
import com.gynda.fridaystm.data.model.PresensiRecord
import com.gynda.fridaystm.data.model.User
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import com.gynda.fridaystm.data.model.AttendanceDayProjector

/** All sources must load successfully before a day list can be considered available. */
interface AttendanceReadRepository {
    fun observeUser(userId: String): Flow<List<AttendanceDay>>
    fun observeClass(kelas: String, date: String): Flow<List<AttendanceDay>>
    suspend fun getUser(userId: String): Result<List<AttendanceDay>>
    suspend fun getClass(kelas: String, startDate: String, endDate: String): Result<List<AttendanceDay>>
}

internal sealed interface AttendanceReadScope {
    data class Owner(val uid: String) : AttendanceReadScope {
        init { require(uid.isNotBlank()) { "UID kosong" } }
    }
    data class ClassRange(val kelas: String, val start: String, val end: String) : AttendanceReadScope {
        init {
            require(kelas.isNotBlank()) { "Kelas kosong" }
            require(LocalDate.parse(start).toString() == start && LocalDate.parse(end).toString() == end && start <= end) {
                "Rentang tanggal tidak valid"
            }
        }
    }
}
internal data class AttendanceReadSnapshot(
    val attendance: List<AttendanceRecord> = emptyList(),
    val presensi: List<PresensiRecord> = emptyList(),
    val roster: List<User> = emptyList(),
)
internal interface AttendanceReadSource {
    fun observe(scope: AttendanceReadScope): Flow<AttendanceReadSnapshot>
    suspend fun get(scope: AttendanceReadScope): AttendanceReadSnapshot
}

internal class CompatibleAttendanceReadRepository(private val source: AttendanceReadSource) : AttendanceReadRepository {
    override fun observeUser(userId: String): Flow<List<AttendanceDay>> =
        source.observe(AttendanceReadScope.Owner(userId)).map { ownerDays(it, userId) }
    override fun observeClass(kelas: String, date: String): Flow<List<AttendanceDay>> =
        source.observe(AttendanceReadScope.ClassRange(kelas, date, date)).map { classDays(it, kelas, date, date) }
    override suspend fun getUser(userId: String): Result<List<AttendanceDay>> = runCatching {
        ownerDays(source.get(AttendanceReadScope.Owner(userId)), userId)
    }.onFailure { if (it is CancellationException) throw it }
    private fun ownerDays(snapshot: AttendanceReadSnapshot, uid: String): List<AttendanceDay> =
        AttendanceDayProjector.merge(snapshot.attendance.filter { it.uid == uid }, snapshot.presensi.filter { it.userId == uid })
    override suspend fun getClass(kelas: String, startDate: String, endDate: String): Result<List<AttendanceDay>> = runCatching {
        classDays(source.get(AttendanceReadScope.ClassRange(kelas, startDate, endDate)), kelas, startDate, endDate)
    }.onFailure { if (it is CancellationException) throw it }
    private fun classDays(snapshot: AttendanceReadSnapshot, kelas: String, start: String, end: String): List<AttendanceDay> {
        val ids = snapshot.roster.filter { it.kelas == kelas }.map { it.uid }.toSet()
        return AttendanceDayProjector.merge(snapshot.attendance.filter { it.uid in ids },
            snapshot.presensi.filter { it.userId in ids }).filter { it.date in start..end }
    }
}
