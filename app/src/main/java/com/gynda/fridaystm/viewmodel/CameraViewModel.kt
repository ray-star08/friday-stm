package com.gynda.fridaystm.viewmodel

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gynda.fridaystm.R
import com.gynda.fridaystm.data.repository.CloudinaryUploader
import com.gynda.fridaystm.data.repository.SelfieUploader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives [com.gynda.fridaystm.ui.screen.CameraCaptureScreen]: holds the captured
 * selfie and orchestrates its compression + upload to Cloudinary.
 *
 * SKILL.md compliance: no `Context`/Compose types here (the `Bitmap` is a plain
 * data payload handed up from the composable); the upload is a `suspend` in the
 * repository returning `Result`; state is a single `StateFlow`.
 */
class CameraViewModel(
    private val selfieUploader: SelfieUploader,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CameraUiState>(CameraUiState.Capturing)
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    /** User took a photo → move to review with the captured [bitmap]. */
    fun onCaptured(bitmap: Bitmap) {
        _uiState.value = CameraUiState.Review(bitmap)
    }

    /** Capture failed → surface an error but stay in the capture state. */
    fun onCaptureError() {
        _uiState.value = CameraUiState.Capturing
    }

    /** User rejected the preview → back to the live camera. */
    fun onRetake() {
        _uiState.value = CameraUiState.Capturing
    }

    /**
     * User accepted the preview → compress + upload, then emit the secure URL.
     * On failure the review is kept so the user can retry.
     */
    fun onConfirm(uid: String, date: String, phase: String) {
        val review = _uiState.value as? CameraUiState.Review ?: return
        _uiState.value = CameraUiState.Uploading(review.bitmap)
        viewModelScope.launch {
            _uiState.value = selfieUploader.uploadSelfie(uid, date, phase, review.bitmap).fold(
                onSuccess = { url -> CameraUiState.Uploaded(review.bitmap, url) },
                onFailure = { CameraUiState.Review(review.bitmap, R.string.camera_upload_error) },
            )
        }
    }

    companion object {
        fun factory(
            selfieUploader: SelfieUploader = CloudinaryUploader(),
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { CameraViewModel(selfieUploader) }
        }
    }
}

/** Exhaustive state for the selfie capture flow. */
sealed interface CameraUiState {
    /** Live preview, waiting for the shutter. */
    data object Capturing : CameraUiState

    /** Photo taken, awaiting user confirm/retake. [errorResId] set after a failed upload. */
    data class Review(val bitmap: Bitmap, @StringRes val errorResId: Int? = null) : CameraUiState

    /** Upload in flight. */
    data class Uploading(val bitmap: Bitmap) : CameraUiState

    /** Done — [secureUrl] is ready to be attached to the attendance write. */
    data class Uploaded(val bitmap: Bitmap, val secureUrl: String) : CameraUiState
}
