package com.gynda.fridaystm.data.model

import com.gynda.fridaystm.util.AttendanceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AttendanceDayProjectorTest {
    @Test
    fun `merge deduplicates legacy school days without upgrading canonical lifecycle`() {
        val partial = AttendanceRecord(uid = "a", date = "2026-09-18",
            pembiasaan = PembiasaanStamp(checkedIn = true, valid = true, time = "06:45"))
        val early = PresensiRecord(id = "early", userId = "a", timestamp = "2026-09-17T23:35:00Z")
        val late = early.copy(id = "late", timestamp = "2026-09-18T07:15:00")
        val standalone = early.copy(id = "standalone", userId = "b", timestamp = "2026-09-18T06:55:00", imageUrl = "https://example.invalid/b.jpg")
        for (input in listOf(listOf(late, standalone, early), listOf(early, standalone, late))) {
            val days = AttendanceDayProjector.merge(listOf(partial), input)
            assertEquals(listOf("a_2026-09-18", "b_2026-09-18"), days.map { it.id })
            assertEquals(AttendanceDayStatus.PARTIAL, days[0].status)
            assertEquals("early", days[0].presensi?.id)
            assertEquals(AttendanceDayStatus.LEGACY, days[1].status)
            assertEquals(null, days[1].attendance)
            assertEquals("06:55", days[1].time)
            assertEquals(standalone.imageUrl, days[1].imageUrl)
        }
        val flagged = partial.copy(status = AttendanceStatus.FLAGGED)
        assertEquals(AttendanceDayStatus.NEEDS_REVIEW,
            AttendanceDayProjector.merge(listOf(flagged), listOf(early)).single().status)
    }

    @Test
    fun `malformed identity or dates and conflicting canonical duplicates fail explicitly`() {
        val record = AttendanceRecord(uid = "u", date = "2026-09-18")
        val badCanonical = listOf(record.copy(uid = ""), record.copy(date = "2026-02-30"), record.copy(date = "not-a-date"))
        for (bad in badCanonical) {
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                AttendanceDayProjector.merge(listOf(bad), emptyList())
            }
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            AttendanceDayProjector.merge(listOf(record, record.copy(status = "flagged")), emptyList())
        }
        assertEquals(1, AttendanceDayProjector.merge(listOf(record, record), emptyList()).size)
        for (bad in listOf(PresensiRecord(userId = "", timestamp = "2026-09-18T06:45:00"),
            PresensiRecord(userId = "u", timestamp = "invalid"))) {
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                AttendanceDayProjector.merge(emptyList(), listOf(bad))
            }
        }
    }

    @Test
    fun `canonical completion requires valid pembiasaan and matching checkout state`() {
        val valid = PembiasaanStamp(checkedIn = true, valid = true, time = "06:45", selfieUrl = "https://real/photo")
        val complete = AttendanceRecord(uid = "student", date = "2026-09-18", pembiasaan = valid,
            checkout = CheckoutStamp(checkedOut = true), status = AttendanceStatus.COMPLETE)
        val cases = listOf(
            complete to AttendanceDayStatus.COMPLETE,
            complete.copy(status = AttendanceStatus.FLAGGED) to AttendanceDayStatus.NEEDS_REVIEW,
            complete.copy(status = "unknown") to AttendanceDayStatus.NEEDS_REVIEW,
            complete.copy(status = AttendanceStatus.INCOMPLETE) to AttendanceDayStatus.NEEDS_REVIEW,
            complete.copy(pembiasaan = null) to AttendanceDayStatus.NEEDS_REVIEW,
            complete.copy(pembiasaan = valid.copy(valid = false)) to AttendanceDayStatus.NEEDS_REVIEW,
            complete.copy(pembiasaan = valid.copy(checkedIn = false)) to AttendanceDayStatus.NEEDS_REVIEW,
            complete.copy(checkout = null) to AttendanceDayStatus.NEEDS_REVIEW,
            complete.copy(checkout = null, status = AttendanceStatus.INCOMPLETE) to AttendanceDayStatus.PARTIAL,
            complete.copy(pembiasaan = null, checkout = null, status = AttendanceStatus.INCOMPLETE,
                apel = ApelStamp(checkedIn = true, valid = true)) to AttendanceDayStatus.PARTIAL,
            complete.copy(pembiasaan = null, apel = ApelStamp(checkedIn = true, valid = true)) to AttendanceDayStatus.NEEDS_REVIEW,
            complete.copy(checkout = null, status = AttendanceStatus.INCOMPLETE,
                apel = ApelStamp(checkedIn = true, valid = false)) to AttendanceDayStatus.NEEDS_REVIEW,
        )
        cases.forEach { (record, expected) ->
            val day = AttendanceDayProjector.merge(listOf(record), emptyList()).single()
            assertEquals(record.toString(), expected, day.status)
            assertSame(record, day.attendance)
            assertEquals(expected != AttendanceDayStatus.NEEDS_REVIEW, day.countsAsPresent)
        }
        val day = AttendanceDayProjector.merge(listOf(complete), emptyList()).single()
        assertEquals("student_2026-09-18", day.id)
        assertEquals("06:45", day.time)
        assertEquals("https://real/photo", day.imageUrl)
    }
}
