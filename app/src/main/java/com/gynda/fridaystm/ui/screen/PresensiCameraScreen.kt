package com.gynda.fridaystm.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gynda.fridaystm.R
import com.gynda.fridaystm.data.local.AppDatabase
import com.gynda.fridaystm.data.local.RoomPendingPresensiStore
import com.gynda.fridaystm.data.repository.AppPendingPhotoCache
import com.gynda.fridaystm.data.repository.CloudinaryStorageRepository
import com.gynda.fridaystm.data.repository.FirebaseAuthRepository
import com.gynda.fridaystm.data.repository.FirebasePresensiRepository
import com.gynda.fridaystm.data.repository.OfflineFirstPresensiRepository
import com.gynda.fridaystm.util.AndroidNetworkMonitor
import com.gynda.fridaystm.util.FusedLocationProvider
import com.gynda.fridaystm.util.SystemTimeProvider
import com.gynda.fridaystm.util.WorkManagerPresensiSyncScheduler
import com.gynda.fridaystm.util.MAX_RADIUS_METERS
import com.gynda.fridaystm.util.calculateDistanceToSchool
import com.gynda.fridaystm.viewmodel.PresensiCameraUiState
import com.gynda.fridaystm.viewmodel.PresensiCameraViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Presensi Camera Screen with full CameraX preview, dual permission handling,
 * and watermark → storage → firestore pipeline state overlays.
 *
 * @param onSuccessNavigateBack invoked on Success after user acknowledges dialog.
 */
@Composable
fun PresensiCameraScreen(
    onSuccessNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PresensiCameraViewModel = viewModel(
        factory = run {
            val appContext = LocalContext.current.applicationContext
            val authRepository = FirebaseAuthRepository()
            val offlineRepository = OfflineFirstPresensiRepository(
                authRepository = authRepository,
                networkMonitor = AndroidNetworkMonitor(appContext),
                queue = RoomPendingPresensiStore(AppDatabase.get(appContext).pendingPresensiDao()),
                photoCache = AppPendingPhotoCache(appContext),
                storageRepository = CloudinaryStorageRepository(),
                presensiRepository = FirebasePresensiRepository(),
                syncScheduler = WorkManagerPresensiSyncScheduler(appContext),
                timeProvider = SystemTimeProvider(),
            )
            PresensiCameraViewModel.factory(
                authRepository = authRepository,
                locationProvider = FusedLocationProvider(LocalContext.current),
                offlineRepository = offlineRepository,
            )
        }
    ),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // --- Permission handling for CAMERA + FINE_LOCATION ---
    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasLocation by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    val hasAllPermissions = hasCamera && hasLocation

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasCamera = result[Manifest.permission.CAMERA] == true || hasCamera
        hasLocation = result[Manifest.permission.ACCESS_FINE_LOCATION] == true || hasLocation
        // Also check directly in case user granted via settings
        hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    LaunchedEffect(Unit) {
        if (!hasAllPermissions) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }
    }

    // --- State side-effects ---
    LaunchedEffect(uiState) {
        when (val s = uiState) {
            is PresensiCameraUiState.Error -> {
                val result = snackbarHostState.showSnackbar(
                    message = s.message,
                    actionLabel = "Coba Lagi"
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.reset()
                }
            }
            else -> Unit
        }
    }

    if (uiState is PresensiCameraUiState.Success) {
        AlertDialog(
            onDismissRequest = {
                viewModel.reset()
                onSuccessNavigateBack()
            },
            title = { Text("Presensi Berhasil") },
            text = { Text("Foto presensi berhasil diunggah dan dicatat.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.reset()
                    onSuccessNavigateBack()
                }) { Text("OK") }
            }
        )
    }

    if (uiState is PresensiCameraUiState.QueuedOffline) {
        AlertDialog(
            onDismissRequest = {
                viewModel.reset()
                onSuccessNavigateBack()
            },
            title = { Text(stringResource(R.string.presensi_queued_title)) },
            text = { Text(stringResource(R.string.presensi_queued_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.reset()
                    onSuccessNavigateBack()
                }) { Text("OK") }
            }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            !hasAllPermissions -> PermissionFallbackContent(
                hasCamera = hasCamera,
                hasLocation = hasLocation,
                onRequest = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.CAMERA,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        )
                    )
                },
                modifier = Modifier.fillMaxSize()
            )

            else -> CameraPreviewWithShutter(
                uiState = uiState,
                onImageProxy = viewModel::onImageCaptured,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Loading overlay
        if (uiState is PresensiCameraUiState.Loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Memproses Watermark & Mengunggah Presensi...",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )
    }
}

@Composable
private fun PermissionFallbackContent(
    hasCamera: Boolean,
    hasLocation: Boolean,
    onRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Izin Diperlukan",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        val missing = buildList {
            if (!hasCamera) add("Kamera (untuk selfie presensi)")
            if (!hasLocation) add("Lokasi Presisi (untuk watermark & validasi)")
        }.joinToString(", ")
        Text(
            text = "Aplikasi membutuhkan izin $missing agar presensi dapat dicatat.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) {
            Text("Izinkan Akses")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Kamera depan akan digunakan untuk selfie, lokasi untuk watermark.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CameraPreviewWithShutter(
    uiState: PresensiCameraUiState,
    onImageProxy: (ImageProxy) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isLoading = uiState is PresensiCameraUiState.Loading

    // --- Real-time distance chip ---
    var distance by remember { mutableStateOf<Float?>(null) }
    var isWithin by remember { mutableStateOf<Boolean?>(null) }
    val chipLocationProvider = remember(context) { FusedLocationProvider(context) }
    LaunchedEffect(Unit) {
        while (isActive) {
            val fix = chipLocationProvider.currentLocation().getOrNull()
            if (fix != null) {
                val d = calculateDistanceToSchool(fix.lat, fix.lng)
                distance = d
                isWithin = d <= MAX_RADIUS_METERS
            }
            delay(3000)
        }
    }
    val shutterEnabled = !isLoading && (isWithin != false)

    // ImageCapture remembers across recompositions
    val imageCapture = remember { ImageCapture.Builder().build() }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val selector = CameraSelector.DEFAULT_FRONT_CAMERA
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            }
        )

        // Distance chip top center
        distance?.let { d ->
            val within = isWithin == true
            Surface(
                color = if (within) Color(0xFF2E7D32).copy(alpha = 0.85f) else Color(0xFFC62828).copy(alpha = 0.85f),
                contentColor = Color.White,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            ) {
                Text(
                    text = if (within) "Jarak: ${d.toInt()}m - Dalam Radius"
                    else "Jarak: ${d.toInt()}m - Di Luar Area",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }

        // Shutter button bottom center
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = CircleShape,
                color = if (shutterEnabled) Color.White else Color.Gray,
                shadowElevation = 8.dp,
                modifier = Modifier.size(80.dp)
            ) {
                IconButton(
                    onClick = {
                        if (!shutterEnabled) return@IconButton
                        imageCapture.takePicture(
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    onImageProxy(image)
                                    // Do NOT close here; ViewModel closes in finally
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    // ViewModel will handle via Error state if we want,
                                    // for now ignore or could emit error via callback
                                }
                            }
                        )
                    },
                    enabled = shutterEnabled,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Face,
                        contentDescription = "Ambil Foto Presensi",
                        tint = Color.Black,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}
