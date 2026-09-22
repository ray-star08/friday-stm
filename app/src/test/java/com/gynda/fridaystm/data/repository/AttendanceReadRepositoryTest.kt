package com.gynda.fridaystm.data.repository

import com.gynda.fridaystm.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AttendanceReadRepositoryTest {
    private class FakeSource : AttendanceReadSource {
        val state = MutableStateFlow(AttendanceReadSnapshot())
        var scope: AttendanceReadScope? = null
        override fun observe(scope: AttendanceReadScope) = state.also { this.scope = scope }
        override suspend fun get(scope: AttendanceReadScope): AttendanceReadSnapshot {
            this.scope = scope
            return state.value
        }
    }

    @Test
    fun `cancellation propagates while data failures remain explicit`() = runTest {
        val failure = IllegalStateException("permission denied")
        val source = object : AttendanceReadSource {
            var cancelled = false
            override fun observe(scope: AttendanceReadScope) = kotlinx.coroutines.flow.flow<AttendanceReadSnapshot> { throw failure }
            override suspend fun get(scope: AttendanceReadScope): AttendanceReadSnapshot {
                if (cancelled) throw kotlinx.coroutines.CancellationException("account changed")
                throw failure
            }
        }
        val repo = CompatibleAttendanceReadRepository(source)
        assertSame(failure, repo.getUser("u").exceptionOrNull())
        try { repo.observeUser("u").first(); fail("Must propagate stream error") } catch (e: IllegalStateException) { assertSame(failure, e) }
        source.cancelled = true
        try { repo.getUser("u"); fail("Must not wrap cancellation") } catch (_: kotlinx.coroutines.CancellationException) { }
        try { repo.getClass("XI A", "2026-09-18", "2026-09-18"); fail("Must not wrap cancellation") } catch (_: kotlinx.coroutines.CancellationException) { }
    }

    @Test
    fun `invalid query scopes are rejected before IO`() = runTest {
        val source = FakeSource()
        val repo = CompatibleAttendanceReadRepository(source)
        assertTrue(repo.getUser("").isFailure)
        assertTrue(repo.getClass("XI A", "2026-09-18", "2026-09-11").isFailure)
        assertTrue(repo.getClass("XI A", "2026-02-30", "2026-09-18").isFailure)
        assertNull(source.scope)
    }

    @Test
    fun `class reader bounds dates and intersects both sources with current roster`() = runTest {
        val source = FakeSource()
        source.state.value = AttendanceReadSnapshot(
            roster = listOf(User(uid = "u", kelas = "XI A"), User(uid = "outsider", kelas = "XI B")),
            attendance = listOf(AttendanceRecord(uid = "u", date = "2026-09-18"),
                AttendanceRecord(uid = "u", date = "2026-09-11"), AttendanceRecord(uid = "outsider", date = "2026-09-18")),
            presensi = listOf(PresensiRecord(userId = "u", timestamp = "2026-09-18T06:45:00", studentClass = "XI A"),
                PresensiRecord(userId = "outsider", timestamp = "2026-09-18T06:45:00", studentClass = "XI A")),
        )
        val repo = CompatibleAttendanceReadRepository(source)
        val days = repo.observeClass("XI A", "2026-09-18").first()
        assertEquals(listOf("u_2026-09-18"), days.map { it.id })
        assertEquals(days, repo.getClass("XI A", "2026-09-18", "2026-09-18").getOrThrow())
        assertEquals(AttendanceReadScope.ClassRange("XI A", "2026-09-18", "2026-09-18"), source.scope)
    }

    @Test
    fun `user reader joins both sources and excludes other owners`() = runTest {
        val source = FakeSource()
        source.state.value = AttendanceReadSnapshot(
            attendance = listOf(AttendanceRecord(uid = "u", date = "2026-09-18",
                pembiasaan = PembiasaanStamp(checkedIn = true, valid = true)),
                AttendanceRecord(uid = "foreign", date = "2026-09-18")),
            presensi = listOf(PresensiRecord(userId = "u", timestamp = "2026-09-18T06:45:00"),
                PresensiRecord(userId = "u", timestamp = "2026-09-11T06:45:00")),
        )
        val repo = CompatibleAttendanceReadRepository(source)
        val observed = repo.observeUser("u").first()
        assertEquals(listOf("u_2026-09-18", "u_2026-09-11"), observed.map { it.id })
        assertEquals(AttendanceDayStatus.PARTIAL, observed.first().status)
        assertEquals(observed, repo.getUser("u").getOrThrow())
        assertEquals(AttendanceReadScope.Owner("u"), source.scope)
    }
}
