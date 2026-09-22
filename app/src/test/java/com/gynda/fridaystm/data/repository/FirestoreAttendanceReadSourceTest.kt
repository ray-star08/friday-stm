package com.gynda.fridaystm.data.repository

import com.gynda.fridaystm.data.model.AttendanceRecord
import com.gynda.fridaystm.data.model.PresensiRecord
import com.gynda.fridaystm.data.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreAttendanceReadSourceTest {
    private class Gateway : AttendanceQueryGateway {
        val requests = mutableListOf<AttendanceQuery>()
        val canonical = MutableSharedFlow<List<AttendanceRecord>>()
        val legacy = MutableSharedFlow<List<PresensiRecord>>()
        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> observe(query: AttendanceQuery, type: Class<T>): Flow<List<T>> {
            requests += query
            return (if (type == AttendanceRecord::class.java) canonical else legacy) as Flow<List<T>>
        }
        override suspend fun <T : Any> get(query: AttendanceQuery, type: Class<T>): List<T> {
            requests += query
            return if (type == User::class.java) listOf(checkNotNull(type.cast(User(uid = "u", kelas = "XI A")))) else emptyList()
        }
    }
    @Test
    fun `owner source waits for both queries and never reads an unconstrained collection`() = runTest {
        val gateway = Gateway()
        val source = FirestoreAttendanceReadSource(gateway)
        val outputs = mutableListOf<AttendanceReadSnapshot>()
        backgroundScope.launch { source.observe(AttendanceReadScope.Owner("u")).collect { outputs += it } }
        runCurrent()
        assertTrue(outputs.isEmpty())
        gateway.canonical.emit(listOf(AttendanceRecord(uid = "u", date = "2026-09-18")))
        runCurrent()
        assertTrue(outputs.isEmpty())
        gateway.legacy.emit(emptyList())
        runCurrent()
        assertEquals(1, outputs.size)
        assertEquals(listOf("u"), outputs.single().attendance.map { it.uid })
        assertEquals(setOf(
            AttendanceQuery("attendance", listOf(AttendanceQueryFilter("uid", "u"))),
            AttendanceQuery("presensi_records", listOf(AttendanceQueryFilter("userId", "u"))),
        ), gateway.requests.toSet())
        gateway.requests.clear()
        source.get(AttendanceReadScope.ClassRange("XI A", "2026-09-11", "2026-09-18"))
        assertEquals(3, gateway.requests.size)
        assertEquals(AttendanceQuery("attendance", listOf(
            AttendanceQueryFilter("date", "2026-09-11", ">="), AttendanceQueryFilter("date", "2026-09-18", "<="))),
            gateway.requests.single { it.collection == "attendance" })
        assertEquals(AttendanceQuery("users", listOf(AttendanceQueryFilter("kelas", "XI A"))),
            gateway.requests.single { it.collection == "users" })
    }
}
