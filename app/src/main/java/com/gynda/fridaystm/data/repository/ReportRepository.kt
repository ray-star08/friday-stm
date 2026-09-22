package com.gynda.fridaystm.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.gynda.fridaystm.data.model.IzinRecord
import com.gynda.fridaystm.data.model.LarkamRecord
import com.gynda.fridaystm.data.model.ReportAggregator
import com.gynda.fridaystm.data.model.StudentSummaryReport
import com.gynda.fridaystm.data.model.User
import com.gynda.fridaystm.data.model.schoolCaptureTime
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.ZoneId

interface ReportRepository {
    /** Server-backed summary for current class members; epoch bounds use school-local dates. */
    suspend fun getRekapSummary(kelas: String, startDate: Long, endDate: Long): Result<List<StudentSummaryReport>>
}

internal interface ReportSource {
    suspend fun users(kelas: String): List<User>
    suspend fun izin(ownerIds: Set<String>): List<IzinRecord>
    suspend fun larkam(ownerIds: Set<String>): List<LarkamRecord>
}

internal class CompatibleReportRepository(
    private val source: ReportSource,
    private val attendanceReadRepository: AttendanceReadRepository,
) : ReportRepository {
    override suspend fun getRekapSummary(kelas: String, startDate: Long, endDate: Long): Result<List<StudentSummaryReport>> = runCatching {
        require(kelas.isNotBlank() && startDate <= endDate) { "Filter rekap tidak valid" }
        val zone = ZoneId.of("Asia/Jakarta")
        val start = Instant.ofEpochMilli(startDate).atZone(zone).toLocalDate()
        val end = Instant.ofEpochMilli(endDate).atZone(zone).toLocalDate()
        val users = source.users(kelas).filter { it.kelas == kelas && it.uid.isNotBlank() }
        val ids = users.currentOwnerIds(kelas)
        if (ids.isEmpty()) return@runCatching emptyList()
        val days = attendanceReadRepository.getClass(kelas, start.toString(), end.toString()).getOrThrow()
        val permits = source.izin(ids).filter { it.userId in ids }
        val runs = source.larkam(ids).filter { it.userId in ids }.filter {
            schoolCaptureTime(it.timestamp).toLocalDate() in start..end
        }
        ReportAggregator.aggregateDays(users, days, permits, runs, startDate, endDate)
    }.onFailure { if (it is CancellationException) throw it }
}

/** Read all required inputs successfully; a failed query never becomes an empty export. */
class FirebaseReportRepository(
    firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    attendanceReader: AttendanceReadRepository = FirebaseAttendanceReadRepository(firestore),
) : ReportRepository by CompatibleReportRepository(FirebaseReportSource(firestore), attendanceReader)

internal class FirebaseReportSource(private val gateway: AttendanceQueryGateway) : ReportSource {
    constructor(firestore: FirebaseFirestore) : this(FirebaseAttendanceQueryGateway(firestore))

    override suspend fun users(kelas: String): List<User> = gateway.get(rosterQuery(kelas), User::class.java)

    override suspend fun izin(ownerIds: Set<String>): List<IzinRecord> = gateway.getForOwners(
        AttendanceOwnerCollection.PERMITS, ownerIds, IzinRecord::class.java,
    )

    override suspend fun larkam(ownerIds: Set<String>): List<LarkamRecord> = gateway.getForOwners(
        AttendanceOwnerCollection.LARKAM, ownerIds, ReportLarkamDocument::class.java,
    ).map { it.toRecord() }
}

/** Numeric aliases used by existing Larkam exports. */
internal data class ReportLarkamDocument(
    @com.google.firebase.firestore.DocumentId val id: String = "",
    val userId: String = "",
    val distanceKm: Any? = null,
    val distanceMeters: Any? = null,
    val distanceInMeters: Any? = null,
    val timestamp: String = "",
    val imageUrl: String = "",
) {
    fun toRecord() = LarkamRecord(
        id = id, userId = userId,
        distanceKm = (distanceKm as? Number)?.toFloat() ?: 0f,
        distanceMeters = (distanceMeters as? Number)?.toDouble()
            ?: (distanceInMeters as? Number)?.toDouble() ?: 0.0,
        timestamp = timestamp, imageUrl = imageUrl,
    )
}
