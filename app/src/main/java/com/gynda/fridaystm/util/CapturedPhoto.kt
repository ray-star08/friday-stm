package com.gynda.fridaystm.util

import com.gynda.fridaystm.data.model.CaptureDraft

/** Camera resource owned by one submission; adapters hide Android from the ViewModel. */
interface CapturedPhoto {
    suspend fun jpeg(draft: CaptureDraft): ByteArray
    fun close()
}
