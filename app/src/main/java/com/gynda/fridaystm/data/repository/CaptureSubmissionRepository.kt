package com.gynda.fridaystm.data.repository

import com.gynda.fridaystm.data.model.CaptureDraft

/** Uploaded means a confirmed remote document; queued means durable local evidence. */
interface CaptureSubmissionRepository {
    suspend fun submitCapture(draft: CaptureDraft, imageBytes: ByteArray): Result<PresensiSubmitResult>
}
