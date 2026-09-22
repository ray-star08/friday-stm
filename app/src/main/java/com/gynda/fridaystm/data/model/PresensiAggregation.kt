package com.gynda.fridaystm.data.model

import java.time.LocalDateTime

/** One attendance per student/day, using the earliest parseable capture time. */
internal fun earliestDailyPresensi(records: List<PresensiRecord>): List<PresensiRecord> =
    records.mapNotNull { record ->
        runCatching { record to LocalDateTime.parse(record.timestamp) }.getOrNull()
    }
        .sortedWith(compareBy({ it.second }, { it.first.id }))
        .distinctBy { (record, time) -> record.userId to time.toLocalDate() }
        .map { it.first }
