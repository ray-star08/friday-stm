package com.gynda.fridaystm.util

/**
 * Holder for Larkam finish payload passed to PresensiCameraScreen via navigation.
 * Stores live stats so the selfie watermark can include them and the Firestore
 * save can target `larkam_records`.
 */
object LarkamPayloadHolder {
    var distanceKm: Float? = null
    var durationSeconds: Long? = null
    var durationFormatted: String? = null
    var route: List<Map<String, Double>>? = null

    fun set(
        distanceKm: Float,
        durationSeconds: Long,
        durationFormatted: String,
        route: List<Map<String, Double>> = emptyList(),
    ) {
        this.distanceKm = distanceKm
        this.durationSeconds = durationSeconds
        this.durationFormatted = durationFormatted
        this.route = route
    }

    fun clear() {
        distanceKm = null
        durationSeconds = null
        durationFormatted = null
        route = null
    }

    fun hasPayload(): Boolean = distanceKm != null && durationFormatted != null
}
