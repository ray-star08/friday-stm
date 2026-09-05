package com.gynda.fridaystm.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

/**
 * The weekly Senam video — Firestore document `senam_sessions/{weekId}`.
 *
 * Set by an instructor; students/class-reps read it to follow the routine on the
 * Home dashboard. Only the 11-char YouTube [videoId] is stored (not a full URL),
 * so the player wrapper can build both the embed and thumbnail deterministically.
 *
 * @property weekId ISO week key `yyyy-Www`, e.g. `"2026-W35"` (also the doc id).
 * @property videoId the 11-char YouTube video id, extracted from whatever URL the
 *   instructor pasted (see `extractYouTubeId`).
 * @property title optional human label for the routine.
 * @property setByUid uid of the instructor who set it (audit trail).
 */
data class SenamSession(
    val weekId: String = "",
    val videoId: String = "",
    val title: String = "",
    val setByUid: String = "",
    val setByName: String = "",
    @get:ServerTimestamp
    val serverTime: Timestamp? = null,
)
