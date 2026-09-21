package com.gynda.fridaystm.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

/**
 * Firestore document `presensi_records/{autoId}` — watermarked selfie presensi.
 * Mirrors [PresensiRepository] payload: userId, timestamp (ISO), imageUrl, etc.
 */
data class PresensiRecord(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val timestamp: String = "", // ISO_LOCAL_DATE_TIME e.g. 2026-09-06T08:20:57
    val imageUrl: String = "",
    val studentName: String = "",
    val studentClass: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    @ServerTimestamp
    val createdAt: Timestamp? = null,
)
