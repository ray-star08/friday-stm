package com.gynda.fridaystm.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gynda.fridaystm.R
import com.gynda.fridaystm.ui.component.CameraPreview
import com.gynda.fridaystm.ui.component.PermissionRequestContent
import com.gynda.fridaystm.ui.component.openAppSettings
import com.gynda.fridaystm.viewmodel.CameraUiState
import com.gynda.fridaystm.viewmodel.CameraViewModel
import kotlinx.coroutines.launch

/**
 * Selfie capture flow (task 4.3) — stateful holder.
 *
 * Owns the [CameraViewModel], the runtime CAMERA permission gate (requested right
 * before use, SKILL.md §8), and the capture handle from [CameraPreview]. When the
 * upload finishes it hands the download URL back via [onSelfieReady]; the parent
 * (check-in flow) attaches it to the attendance write.
 *
 * @param uid signed-in student id (path component of the Storage object).
 * @param date ISO `yyyy-MM-dd`; @param phase one of `util.SelfiePhase`.
 */
@Composable
fun CameraCaptureScreen(
    uid: String,
    date: String,
    phase: String,
    onSelfieReady: (downloadUrl: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CameraViewModel,
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permanentlyDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        if (!granted) permanentlyDenied = true
    }

    // Ask for the permission the moment the screen needs it.
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Surface the resulting URL exactly once.
    LaunchedEffect(state) {
        (state as? CameraUiState.Uploaded)?.let { onSelfieReady(it.secureUrl) }
    }

    when {
        !hasPermission -> PermissionRequestContent(
            messageResId = if (permanentlyDenied) R.string.camera_permission_denied
            else R.string.camera_permission_rationale,
            actionLabelResId = if (permanentlyDenied) R.string.permission_open_settings
            else R.string.camera_permission_grant,
            onAction = if (permanentlyDenied) {
                { context.openAppSettings() }
            } else {
                { permissionLauncher.launch(Manifest.permission.CAMERA) }
            },
            modifier = modifier,
        )

        else -> CaptureContent(
            state = state,
            onCaptured = viewModel::onCaptured,
            onCaptureError = viewModel::onCaptureError,
            onRetake = viewModel::onRetake,
            onConfirm = { viewModel.onConfirm(uid, date, phase) },
            modifier = modifier,
        )
    }
}

@Composable
private fun CaptureContent(
    state: CameraUiState,
    onCaptured: (Bitmap) -> Unit,
    onCaptureError: () -> Unit,
    onRetake: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    // Capture handle provided by CameraPreview once the camera is bound.
    var capture by remember { mutableStateOf<(suspend () -> Result<Bitmap>)?>(null) }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = stringResource(R.string.camera_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(12.dp))

        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            when (state) {
                CameraUiState.Capturing -> CameraPreview(
                    onCaptureReady = { capture = it },
                    modifier = Modifier.fillMaxSize(),
                )

                is CameraUiState.Review -> SelfiePreview(state.bitmap)
                is CameraUiState.Uploading -> SelfiePreview(state.bitmap)
                is CameraUiState.Uploaded -> SelfiePreview(state.bitmap)
            }

            if (state is CameraUiState.Uploading) {
                CircularProgressIndicator()
            }
        }

        Spacer(Modifier.height(16.dp))

        when (state) {
            CameraUiState.Capturing -> Button(
                onClick = {
                    val take = capture ?: return@Button
                    scope.launch {
                        take().fold(onSuccess = onCaptured, onFailure = { onCaptureError() })
                    }
                },
                enabled = capture != null,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text(stringResource(R.string.camera_capture)) }

            is CameraUiState.Review -> {
                state.errorResId?.let {
                    Text(
                        text = stringResource(it),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Row2Buttons(
                    onLeft = onRetake,
                    leftText = stringResource(R.string.camera_retake),
                    onRight = onConfirm,
                    rightText = stringResource(R.string.camera_use_photo),
                )
            }

            is CameraUiState.Uploading -> Text(
                text = stringResource(R.string.camera_uploading),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )

            is CameraUiState.Uploaded -> Unit // parent navigates away
        }
    }
}

@Composable
private fun Row2Buttons(
    onLeft: () -> Unit,
    leftText: String,
    onRight: () -> Unit,
    rightText: String,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(onClick = onLeft, modifier = Modifier.weight(1f).height(56.dp)) {
            Text(leftText)
        }
        Button(onClick = onRight, modifier = Modifier.weight(1f).height(56.dp)) {
            Text(rightText)
        }
    }
}

@Composable
private fun SelfiePreview(bitmap: Bitmap, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.cd_selfie_preview)
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = description,
        contentScale = ContentScale.Fit,
        modifier = modifier.fillMaxSize().semantics { contentDescription = description },
    )
}
