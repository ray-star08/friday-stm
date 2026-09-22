package com.gynda.fridaystm.util

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Date-only report inputs are anchored to school midnight, never device midnight. */
object SchoolDates {
    val zone: ZoneId = ZoneId.of("Asia/Jakarta")

    fun today(clock: Clock): LocalDate = LocalDate.now(clock.withZone(zone))
    fun date(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    fun startOfDay(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    /** Material DatePicker encodes a date as UTC midnight, not an attendance instant. */
    fun toPickerMillis(schoolMillis: Long): Long =
        date(schoolMillis).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun fromPickerMillis(utcMillis: Long): Long =
        startOfDay(Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate())
}
