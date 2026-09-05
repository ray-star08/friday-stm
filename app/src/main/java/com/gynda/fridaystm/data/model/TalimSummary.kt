package com.gynda.fridaystm.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.ServerTimestamp

/**
 * A Ta'lim class summary — Firestore document `talim_summaries/{date}_{kelas}`.
 *
 * Submitted once per class per Friday by the class representative (`class_rep`);
 * the deterministic id makes duplicate submissions structurally impossible.
 *
 * Firestore DTO: every property defaults so the SDK can instantiate it via the
 * no-arg constructor during `toObject<TalimSummary>()`.
 *
 * @property date ISO `yyyy-MM-dd`.
 * @property kelas class name, e.g. `"XI RPL 1"`.
 * @property penceramah name of the speaker/ustadz who led the session.
 * @property tema the session theme/topic.
 * @property ringkasan the free-text summary of what was covered.
 * @property submittedByUid uid of the class rep who submitted (audit trail).
 */
data class TalimSummary(
    val date: String = "",
    val kelas: String = "",
    val grade: Int = 0,
    val penceramah: String = "",
    val tema: String = "",
    val ringkasan: String = "",
    val submittedByUid: String = "",
    val submittedByName: String = "",
    @get:ServerTimestamp
    val serverTime: Timestamp? = null,
) {
    @get:Exclude
    val docId: String
        get() = docIdFor(date, kelas)

    companion object {
        /** Deterministic id `"${date}_${kelas}"`, e.g. `"2026-08-14_XI RPL 1"`. */
        fun docIdFor(date: String, kelas: String): String = "${date}_$kelas"
    }
}
