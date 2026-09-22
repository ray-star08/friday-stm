package com.gynda.fridaystm.util

import com.gynda.fridaystm.data.model.LarkamCapture
import java.time.LocalDate

/**
 * A completed run is bound to its originating account and school day.
 * [captureId] identifies only this navigation handoff; each new selfie receives
 * its own immutable CaptureDraft ID, while repository retries reuse that draft.
 */
data class LarkamCaptureIntent(val captureId: String, val ownerUid: String, val date: LocalDate, val run: LarkamCapture)
