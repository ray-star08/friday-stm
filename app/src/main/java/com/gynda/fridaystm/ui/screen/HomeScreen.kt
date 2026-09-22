package com.gynda.fridaystm.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import com.gynda.fridaystm.ui.component.ActionMenuRow
import com.gynda.fridaystm.ui.theme.Spacing
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gynda.fridaystm.R
import com.gynda.fridaystm.data.model.AttendanceRecord
import com.gynda.fridaystm.data.model.PembiasaanStamp
import com.gynda.fridaystm.data.model.User
import com.gynda.fridaystm.domain.Activity
import com.gynda.fridaystm.domain.FridayPhase
import com.gynda.fridaystm.domain.PhaseSchedule
import com.gynda.fridaystm.ui.component.BigActionButton
import com.gynda.fridaystm.ui.component.CountdownTimer
import com.gynda.fridaystm.ui.component.DynamicPhaseCard
import com.gynda.fridaystm.ui.component.GeofenceMiniMap
import com.gynda.fridaystm.ui.component.PermissionRequestContent
import com.gynda.fridaystm.ui.component.SelfiePreviewCard
import com.gynda.fridaystm.ui.component.SenamCard
import com.gynda.fridaystm.ui.component.StatusStepper
import com.gynda.fridaystm.ui.component.TalimCard
import com.gynda.fridaystm.ui.component.activityLabelRes
import com.gynda.fridaystm.ui.component.openAppSettings
import com.gynda.fridaystm.ui.theme.FridaySTMTheme
import com.gynda.fridaystm.BuildConfig
import com.gynda.fridaystm.util.ActivityType
import com.gynda.fridaystm.util.DebugTimeProvider
import com.gynda.fridaystm.util.LocationGateResult
import com.gynda.fridaystm.util.LocationPermissionState
import com.gynda.fridaystm.util.reducePermissionResult
import com.gynda.fridaystm.util.resolveLocationGate
import com.gynda.fridaystm.viewmodel.HomeAction
import com.gynda.fridaystm.viewmodel.HomeUiState
import com.gynda.fridaystm.viewmodel.HomeViewModel
import com.gynda.fridaystm.viewmodel.OfflineQueueViewModel
import com.gynda.fridaystm.viewmodel.SenamUiState
import com.gynda.fridaystm.viewmodel.SenamViewModel
import com.gynda.fridaystm.viewmodel.SubmitStatus
import com.gynda.fridaystm.viewmodel.TalimUiState
import com.gynda.fridaystm.viewmodel.TalimViewModel
import kotlin.math.roundToInt
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Home — stateful holder (SKILL.md §4.1).
 *
 * Owns the [HomeViewModel], collects its state with lifecycle awareness, and
 * translates one-off navigation intents. It renders nothing itself beyond
 * delegating to the stateless [HomeContent], keeping all UI previewable.
 *
 * @param onNavigateToCheckIn opens the Milestone-4 geofence + selfie check-in flow.
 * @param onNavigateToLogin invoked when the session is signed out.
 */
@Composable
fun HomeScreen(
    onNavigateToCheckIn: () -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel,
    senamViewModel: SenamViewModel,
    talimViewModel: TalimViewModel,
    offlineQueueViewModel: OfflineQueueViewModel,
    onOpenLarkam: () -> Unit = {},
    onNavigateToPresensiCamera: () -> Unit = {},
    onOpenPresensiHistory: () -> Unit = {},
    onOpenPengajuanIzin: () -> Unit = {},
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val submitStatus by viewModel.submitStatus.collectAsStateWithLifecycle()
    val senamState by senamViewModel.uiState.collectAsStateWithLifecycle()
    val senamSetStatus by senamViewModel.setStatus.collectAsStateWithLifecycle()
    val talimState by talimViewModel.uiState.collectAsStateWithLifecycle()
    val talimSubmitStatus by talimViewModel.submitStatus.collectAsStateWithLifecycle()
    val pendingCount by offlineQueueViewModel.pendingCount.collectAsStateWithLifecycle()

    // Route to Login exactly once when the session resolves to signed-out.
    val signedOut = uiState is HomeUiState.SignedOut
    LaunchedEffect(signedOut) {
        if (signedOut) onNavigateToLogin()
    }

    // Runtime location permission (task 4.1): the geofence gate is dead without a
    // fix, so we request FINE/COARSE right before the check-in section needs it.
    var locationGranted by rememberSaveable { mutableStateOf(context.hasFineOrCoarseLocation()) }
    var locationPermanentlyDenied by rememberSaveable { mutableStateOf(false) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        // Pure reducer owns the "what does this result mean" decision (task 4.1).
        val state = reducePermissionResult(result)
        locationGranted = state.isGranted
        locationPermanentlyDenied = state.isPermanentlyDenied
    }

    // FCM: POST_NOTIFICATIONS (API 33+) — requested once when entering Dashboard
    // so reminder notifications (presensi/larkam) can be shown heads-up.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> /* best-effort; system drops posts when denied */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Only prompt while an active geofenced check-in actually needs location, and
    // only once per grant state — never on a non-check-in phase (SKILL.md §8).
    val needsLocation = (uiState as? HomeUiState.Ready)?.action is HomeAction.CheckInPembiasaan
    LaunchedEffect(needsLocation, locationGranted) {
        val gate = resolveLocationGate(
            needsLocation,
            LocationPermissionState(locationGranted, locationPermanentlyDenied),
        )
        // Auto-prompt only; NavigateToSettings is a user-driven tap, not automatic.
        if (gate == LocationGateResult.RequestPermission) {
            locationPermissionLauncher.launch(LOCATION_PERMISSIONS)
        }
    }

    // A submitted check-in/out reports Success once; fire a confirm haptic then
    // consume it so it can't re-fire on recomposition. The button itself flips to
    // its "done" state via the record stream, so no success text is rendered here.
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(submitStatus) {
        if (submitStatus is SubmitStatus.Success) {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            viewModel.onSubmitStatusConsumed()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        HomeContent(
            state = uiState,
            submitStatus = submitStatus,
            senamState = senamState,
            senamSetStatus = senamSetStatus,
            talimState = talimState,
            talimSubmitStatus = talimSubmitStatus,
            locationGranted = locationGranted,
            locationPermanentlyDenied = locationPermanentlyDenied,
            onRequestLocationPermission = { locationPermissionLauncher.launch(LOCATION_PERMISSIONS) },
            onOpenLocationSettings = { context.openAppSettings() },
            onPembiasaanCheckIn = onNavigateToCheckIn,
            onCheckOut = viewModel::onCheckOut,
            onSetSenamVideo = senamViewModel::onSetVideo,
            onSubmitTalim = talimViewModel::onSubmit,
            onOpenLarkam = onOpenLarkam,
            onNavigateToPresensiCamera = onNavigateToPresensiCamera,
            onOpenPresensiHistory = onOpenPresensiHistory,
            onOpenPengajuanIzin = onOpenPengajuanIzin,
            pendingCount = pendingCount,
            onSyncNow = offlineQueueViewModel::syncNow,
            modifier = Modifier.fillMaxSize(),
        )
        if (BuildConfig.DEBUG) {
            val debugProvider = viewModel.debugTimeProvider
            if (debugProvider != null) {
                DebugControlBar(
                    debugProvider = debugProvider,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

/** Location permissions requested together; either grants a usable fix. */
private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

private fun android.content.Context.hasFineOrCoarseLocation(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Home — stateless content. Renders purely from [state] + [submitStatus] and
 * raises intents through the callbacks, so every branch is previewable.
 */
@Composable
fun HomeContent(
    state: HomeUiState,
    submitStatus: SubmitStatus,
    senamState: SenamUiState,
    senamSetStatus: SubmitStatus,
    talimState: TalimUiState,
    talimSubmitStatus: SubmitStatus,
    onPembiasaanCheckIn: () -> Unit,
    onCheckOut: () -> Unit,
    onSetSenamVideo: (String) -> Unit,
    onSubmitTalim: (String, String, String) -> Unit,
    modifier: Modifier = Modifier,
    locationGranted: Boolean = true,
    locationPermanentlyDenied: Boolean = false,
    onRequestLocationPermission: () -> Unit = {},
    onOpenLocationSettings: () -> Unit = {},
    onOpenLarkam: () -> Unit = {},
    onNavigateToPresensiCamera: () -> Unit = {},
    onOpenPresensiHistory: () -> Unit = {},
    onOpenPengajuanIzin: () -> Unit = {},
    pendingCount: Int = 0,
    onSyncNow: () -> Unit = {},
) {
    // Smooth fade between Loading / Ready / Error rather than a hard swap (M5.3).
    Crossfade(targetState = state, label = "home-state", modifier = modifier) { s ->
        when (s) {
            HomeUiState.Loading,
            HomeUiState.SignedOut,
            -> CenteredSpinner()

            is HomeUiState.Error -> CenteredMessage(stringResource(s.messageResId))

            is HomeUiState.Ready -> ReadyContent(
                state = s,
                submitStatus = submitStatus,
                senamState = senamState,
                senamSetStatus = senamSetStatus,
                talimState = talimState,
                talimSubmitStatus = talimSubmitStatus,
                locationGranted = locationGranted,
                locationPermanentlyDenied = locationPermanentlyDenied,
                onRequestLocationPermission = onRequestLocationPermission,
                onOpenLocationSettings = onOpenLocationSettings,
                onPembiasaanCheckIn = onPembiasaanCheckIn,
                onCheckOut = onCheckOut,
                onSetSenamVideo = onSetSenamVideo,
                onSubmitTalim = onSubmitTalim,
                onOpenLarkam = onOpenLarkam,
                onNavigateToPresensiCamera = onNavigateToPresensiCamera,
                onOpenPresensiHistory = onOpenPresensiHistory,
                onOpenPengajuanIzin = onOpenPengajuanIzin,
                pendingCount = pendingCount,
                onSyncNow = onSyncNow,
            )
        }
    }
}

@Composable
private fun ReadyContent(
    state: HomeUiState.Ready,
    submitStatus: SubmitStatus,
    senamState: SenamUiState,
    senamSetStatus: SubmitStatus,
    talimState: TalimUiState,
    talimSubmitStatus: SubmitStatus,
    locationGranted: Boolean,
    locationPermanentlyDenied: Boolean,
    onRequestLocationPermission: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onPembiasaanCheckIn: () -> Unit,
    onCheckOut: () -> Unit,
    onSetSenamVideo: (String) -> Unit,
    onSubmitTalim: (String, String, String) -> Unit,
    onOpenLarkam: () -> Unit,
    onNavigateToPresensiCamera: () -> Unit = {},
    onOpenPresensiHistory: () -> Unit = {},
    onOpenPengajuanIzin: () -> Unit = {},
    pendingCount: Int = 0,
    onSyncNow: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val submitting = submitStatus is SubmitStatus.Submitting
    val name = state.user.nama.ifBlank { stringResource(R.string.home_default_name) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.s20),
    ) {
        Text(
            stringResource(R.string.redesign_home_eyebrow),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(Spacing.s8))
        Text(
            text = stringResource(R.string.home_greeting, name),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(state.user.kelas, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.s24))

        // Offline queue banner: visible only while captures await signal.
        if (pendingCount > 0) {
            OfflineQueueBanner(
                count = pendingCount,
                onSyncNow = onSyncNow,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
        }

        DynamicPhaseCard(
            phase = state.phase,
            activeActivity = state.activeActivity,
        )

        // Special-week override banner (task 3.3): only when today's activity came
        // from a Firestore override, and only on a day the schedule is shown.
        if (state.isSpecialWeek && state.phase != FridayPhase.NOT_FRIDAY) {
            Spacer(Modifier.height(12.dp))
            SpecialWeekChip()
        }

        // Overall Pembiasaan → Check-out progress. Meaningless off-Friday.
        if (state.phase != FridayPhase.NOT_FRIDAY) {
            Spacer(Modifier.height(24.dp))
            StatusStepper(
                phase = state.phase,
                activeActivity = state.activeActivity,
                record = state.record,
            )
        }

        // Live countdown to the current phase's deadline (M5.3).
        phaseDeadlineEpochMillis(state.phase)?.let { deadline ->
            Spacer(Modifier.height(16.dp))
            CountdownTimer(
                targetEpochMillis = deadline,
                label = stringResource(phaseDeadlineLabelRes(state.phase)),
            )
        }

        // Selfie evidence for a completed check-in in the current phase (M5.1).
        currentPhaseSelfie(state)?.let { (url, statusRes, time, valid) ->
            Spacer(Modifier.height(16.dp))
            SelfiePreviewCard(
                selfieUrl = url,
                statusLabel = stringResource(statusRes),
                time = time,
                valid = valid,
            )
        }

        val spec = actionSpecFor(state.action)
        if (spec != null) {
            val geofenceGated = state.action is HomeAction.CheckInPembiasaan

            if (geofenceGated && !locationGranted) {
                // No location permission ⇒ no fix ⇒ the geofence gate can never open.
                // Prompt for it here instead of showing a permanently-disabled button (task 4.1).
                Spacer(Modifier.height(12.dp))
                PermissionRequestContent(
                    messageResId = if (locationPermanentlyDenied) R.string.location_permission_denied
                    else R.string.location_permission_rationale,
                    actionLabelResId = if (locationPermanentlyDenied) R.string.permission_open_settings
                    else R.string.location_permission_grant,
                    onAction = if (locationPermanentlyDenied) onOpenLocationSettings
                    else onRequestLocationPermission,
                )
            } else {
                // Check-in actions are additionally gated by the live geofence check
                // (Milestone 4.2); "done"/check-out specs keep their own enabled state.
                val enabled = if (geofenceGated) spec.enabled && state.isInsideGeofence else spec.enabled

                if (geofenceGated) {
                    GeofenceHint(
                        inside = state.isInsideGeofence,
                        distanceMeters = state.distanceMeters,
                        targetLabel = state.geofenceTarget?.label,
                    )
                    Spacer(Modifier.height(12.dp))

                    // Mini-map: only meaningful with a resolved fence to draw around.
                    state.geofenceTarget?.let { target ->
                        var recenterKey by remember { mutableIntStateOf(0) }
                        Box(Modifier.fillMaxWidth().height(220.dp)) {
                            GeofenceMiniMap(
                                centerLat = target.lat,
                                centerLng = target.lng,
                                radiusMeter = target.radiusMeter,
                                userLat = state.userLat,
                                userLng = state.userLng,
                                recenterKey = recenterKey,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(16.dp)),
                            )
                            if (state.userLat != null) {
                                FilledTonalIconButton(
                                    onClick = { recenterKey++ },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(12.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Place,
                                        contentDescription = stringResource(R.string.cd_minimap_recenter),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }

                Spacer(Modifier.height(12.dp))
                BigActionButton(
                    text = spec.text,
                    onClick = when (state.action) {
                        is HomeAction.CheckInPembiasaan -> onPembiasaanCheckIn
                        HomeAction.CheckOut -> onCheckOut
                        else -> ({}) // "done" states are disabled and non-clickable
                    },
                    enabled = enabled,
                    loading = enabled && submitting,
                    icon = spec.icon,
                    containerColor = spec.container,
                    contentColor = spec.content,
                )
            }
        }

        if (submitStatus is SubmitStatus.Error) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(submitStatus.messageResId),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(Spacing.s24))
        Text(stringResource(R.string.redesign_tools), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.s12))
        ActionMenuRow(
            title = stringResource(R.string.redesign_selfie),
            subtitle = stringResource(R.string.redesign_selfie_body),
            icon = Icons.Filled.Face,
            onClick = onNavigateToPresensiCamera,
        )
        Spacer(Modifier.height(Spacing.s8))
        ActionMenuRow(
            title = stringResource(R.string.redesign_history),
            subtitle = stringResource(R.string.redesign_history_body),
            icon = Icons.Filled.DateRange,
            onClick = onOpenPresensiHistory,
        )
        Spacer(Modifier.height(Spacing.s8))
        ActionMenuRow(
            title = stringResource(R.string.izin_open),
            subtitle = stringResource(R.string.redesign_izin_body),
            icon = Icons.Filled.Edit,
            onClick = onOpenPengajuanIzin,
        )

        // Larkam run tracker (Activity 4): only during Pembiasaan when the class is
        // rotated to Larkam and the role attends. Selfie check-in stays the attendance
        // proof; this opens the live map + Firestore-logged run on top of it.
        if (state.phase == FridayPhase.PEMBIASAAN &&
            state.activeActivity == Activity.LARKAM &&
            state.user.roleEnum.canAttendPembiasaan
        ) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onOpenLarkam, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.LocationOn, contentDescription = null)
                Text(
                    text = stringResource(R.string.larkam_open),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        // Weekly Senam video (Activity 3) — self-gates on role/availability.
        Spacer(Modifier.height(24.dp))
        SenamCard(
            state = senamState,
            setStatus = senamSetStatus,
            onSetVideo = onSetSenamVideo,
        )

        // Ta'lim class summary (Activity 2) — self-gates: class-rep form / read-back.
        Spacer(Modifier.height(24.dp))
        TalimCard(
            state = talimState,
            submitStatus = talimSubmitStatus,
            onSubmit = onSubmitTalim,
        )
    }
}

/** Resolved button presentation for a [HomeAction]; `null` ⇒ render no button. */
private data class ActionSpec(
    val text: String,
    val enabled: Boolean,
    val icon: ImageVector,
    val container: Color,
    val content: Color,
)

@Composable
private fun actionSpecFor(action: HomeAction): ActionSpec? {
    val scheme = MaterialTheme.colorScheme
    return when (action) {
        HomeAction.None -> null

        is HomeAction.CheckInPembiasaan -> ActionSpec(
            text = stringResource(
                R.string.action_check_in_pembiasaan,
                stringResource(activityLabelRes(action.activity)),
            ),
            enabled = true,
            icon = Icons.Filled.LocationOn,
            container = scheme.primary,
            content = scheme.onPrimary,
        )

        is HomeAction.PembiasaanDone -> ActionSpec(
            text = stringResource(
                R.string.action_pembiasaan_done,
                stringResource(activityLabelRes(action.activity)),
            ),
            enabled = false,
            icon = Icons.Filled.CheckCircle,
            container = scheme.secondary,
            content = scheme.onSecondary,
        )

        HomeAction.CheckOut -> ActionSpec(
            text = stringResource(R.string.action_check_out),
            enabled = true,
            icon = Icons.Filled.CheckCircle,
            container = scheme.tertiary,
            content = scheme.onTertiary,
        )

        HomeAction.CheckedOut -> ActionSpec(
            text = stringResource(R.string.action_checked_out),
            enabled = false,
            icon = Icons.Filled.CheckCircle,
            container = scheme.secondary,
            content = scheme.onSecondary,
        )
    }
}

@Composable
private fun CenteredSpinner(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/**
 * Pill badge shown when today's activity comes from a Firestore rotation override
 * rather than the cyclic formula (task 3.3). Non-interactive: it labels state, so
 * it is a [Surface], not a chip with a phantom `onClick`. The short visible text
 * is replaced for screen readers by the fuller [R.string.cd_special_week_badge].
 */
@Composable
private fun SpecialWeekChip(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.cd_special_week_badge)
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(percent = 50),
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.DateRange,
                contentDescription = null, // decorative; the Surface carries the label
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.home_special_week_badge),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/**
 * Epoch-millis deadline of the current phase, or `null` if the phase has no
 * countdown (BEFORE/DONE/NOT_FRIDAY). Anchored to *today* so the timer counts to
 * the real wall-clock boundary. Pure display helper (no ViewModel needed).
 */
private fun phaseDeadlineEpochMillis(phase: FridayPhase): Long? {
    val time = when (phase) {
        FridayPhase.PEMBIASAAN -> PhaseSchedule.CHECKOUT_START
        FridayPhase.CHECKOUT -> PhaseSchedule.CHECKOUT_END
        else -> return null
    }
    return LocalDate.now()
        .atTime(time)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}

@StringRes
private fun phaseDeadlineLabelRes(phase: FridayPhase): Int = when (phase) {
    FridayPhase.PEMBIASAAN -> R.string.countdown_pembiasaan
    else -> R.string.countdown_checkout
}

/** The four display values for a completed-phase [SelfiePreviewCard], or `null`. */
private data class PhaseSelfie(
    val url: String,
    @param:StringRes val statusRes: Int,
    val time: String,
    val valid: Boolean,
)

/**
 * The selfie to preview for the phase the student just completed: the Pembiasaan
 * stamp during PEMBIASAAN. `null` when there is no checked-in stamp with a photo yet.
 */
private fun currentPhaseSelfie(state: HomeUiState.Ready): PhaseSelfie? = when (state.phase) {
    FridayPhase.PEMBIASAAN -> state.record?.pembiasaan
        ?.takeIf { it.checkedIn && it.selfieUrl.isNotBlank() }
        ?.let { PhaseSelfie(it.selfieUrl, R.string.status_presensi_kegiatan, it.time, it.valid) }

    else -> null
}

/**
 * Offline-queue banner shown on the Dashboard while captures await signal:
 * "N Presensi Menunggu Sinyal" + a manual sync action. Pure UI — the count
 * comes from [OfflineQueueViewModel.pendingCount] (SKILL.md §3.3).
 */
@Composable
private fun OfflineQueueBanner(
    count: Int,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.offline_queue_cd)
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null, // decorative; the Surface carries the label
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.offline_queue_banner, count),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onSyncNow) {
                Text(stringResource(R.string.offline_queue_sync))
            }
        }
    }
}

/**
 * Live geofence status line shown above a check-in button: the realtime distance
 * to the target fence, a "locating" hint, or a ready message. Pure UI — the
 * inside/distance decision is made in the ViewModel (SKILL.md §3.3).
 */
@Composable
private fun GeofenceHint(
    inside: Boolean,
    distanceMeters: Double?,
    targetLabel: String?,
    modifier: Modifier = Modifier,
) {
    val label = targetLabel.orEmpty()
    val text = when {
        inside -> stringResource(R.string.geofence_inside, label)
        distanceMeters != null ->
            stringResource(R.string.geofence_distance, distanceMeters.roundToInt(), label)
        else -> stringResource(R.string.geofence_locating)
    }
    val color = if (inside) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun CenteredMessage(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// --- Debug / Demo overlay (only in DEBUG) --------------------------------

/**
 * Floating debug bar for presentations: lets the presenter jump between
 * Friday phases and advance the ISO week to demo the 3-week rotation
 * without changing the device clock. Only rendered when [BuildConfig.DEBUG]
 * and the injected [TimeProvider] is a [DebugTimeProvider].
 */
@Composable
private fun DebugControlBar(
    debugProvider: DebugTimeProvider,
    modifier: Modifier = Modifier,
) {
    val simulated by debugProvider.simulatedTime.collectAsStateWithLifecycle()
    val label = remember(simulated) {
        simulated.format(DateTimeFormatter.ofPattern("EEE dd-MM HH:mm 'W'w"))
    }
    val weekId = remember(simulated) {
        val y = simulated.get(java.time.temporal.WeekFields.ISO.weekBasedYear())
        val w = simulated.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear())
        "%d-W%02d".format(y, w)
    }
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.96f),
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 6.dp,
        modifier = modifier
            .padding(top = 8.dp, start = 12.dp, end = 12.dp)
            .fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "DEBUG \u00B7 $label \u00B7 $weekId",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                DebugChip(
                    text = "Jumat 07:00",
                    onClick = { debugProvider.setFridayPhase(7, 0) },
                    modifier = Modifier.weight(1f),
                )
                DebugChip(
                    text = "Jumat 08:15",
                    onClick = { debugProvider.setFridayPhase(8, 15) },
                    modifier = Modifier.weight(1f),
                )
                DebugChip(
                    text = "+1 Pekan",
                    onClick = { debugProvider.advanceWeek(1) },
                    modifier = Modifier.weight(1f),
                )
                DebugChip(
                    text = "Reset",
                    onClick = { debugProvider.resetToSystem() },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DebugChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(50),
        modifier = modifier,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Text(text = text, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        }
    }
}

// --- Previews ------------------------------------------------------------

private val previewStudent = User(
    uid = "u1",
    nis = "2024011",
    nama = "Budi",
    grade = 11,
    kelas = "XI RPL 1",
    role = "student",
)

private val previewSenamState = SenamUiState(weekId = "2026-W35")

private val previewTalimState = TalimUiState(date = "2026-08-28")

@Preview(showBackground = true, name = "Home · Loading")
@Composable
private fun HomeContentLoadingPreview() {
    FridaySTMTheme {
        HomeContent(
            state = HomeUiState.Loading,
            submitStatus = SubmitStatus.Idle,
            senamState = previewSenamState,
            senamSetStatus = SubmitStatus.Idle,
            talimState = previewTalimState,
            talimSubmitStatus = SubmitStatus.Idle,
            onPembiasaanCheckIn = {},
            onCheckOut = {},
            onSetSenamVideo = {},
            onSubmitTalim = { _, _, _ -> },
        )
    }
}

@Preview(showBackground = true, name = "Home · Pembiasaan (Larkam)")
@Composable
private fun HomeContentPembiasaanPreview() {
    FridaySTMTheme {
        HomeContent(
            state = HomeUiState.Ready(
                user = previewStudent,
                phase = FridayPhase.PEMBIASAAN,
                activeActivity = Activity.LARKAM,
                record = null,
                action = HomeAction.CheckInPembiasaan(Activity.LARKAM),
            ),
            submitStatus = SubmitStatus.Idle,
            senamState = previewSenamState,
            senamSetStatus = SubmitStatus.Idle,
            talimState = previewTalimState,
            talimSubmitStatus = SubmitStatus.Idle,
            onPembiasaanCheckIn = {},
            onCheckOut = {},
            onSetSenamVideo = {},
            onSubmitTalim = { _, _, _ -> },
        )
    }
}

@Preview(showBackground = true, name = "Home · Pembiasaan (special week)")
@Composable
private fun HomeContentSpecialWeekPreview() {
    FridaySTMTheme {
        HomeContent(
            state = HomeUiState.Ready(
                user = previewStudent,
                phase = FridayPhase.PEMBIASAAN,
                activeActivity = Activity.SENAM,
                isSpecialWeek = true, // grade routed by a Firestore override, not the formula
                record = null,
                action = HomeAction.CheckInPembiasaan(Activity.SENAM),
            ),
            submitStatus = SubmitStatus.Idle,
            senamState = previewSenamState,
            senamSetStatus = SubmitStatus.Idle,
            talimState = previewTalimState,
            talimSubmitStatus = SubmitStatus.Idle,
            onPembiasaanCheckIn = {},
            onCheckOut = {},
            onSetSenamVideo = {},
            onSubmitTalim = { _, _, _ -> },
        )
    }
}

@Preview(showBackground = true, name = "Home · Check-out")
@Composable
private fun HomeContentCheckoutPreview() {
    FridaySTMTheme {
        HomeContent(
            state = HomeUiState.Ready(
                user = previewStudent,
                phase = FridayPhase.CHECKOUT,
                activeActivity = Activity.LARKAM,
                // Pembiasaan done: stepper shows step 1 complete, step 2 active.
                record = AttendanceRecord(
                    uid = "u1",
                    date = "2026-08-14",
                    grade = 11,
                    pembiasaan = PembiasaanStamp(
                        activity = ActivityType.LARKAM,
                        checkedIn = true,
                        time = "06:45",
                    ),
                ),
                action = HomeAction.CheckOut,
            ),
            submitStatus = SubmitStatus.Idle,
            senamState = previewSenamState,
            senamSetStatus = SubmitStatus.Idle,
            talimState = previewTalimState,
            talimSubmitStatus = SubmitStatus.Idle,
            onPembiasaanCheckIn = {},
            onCheckOut = {},
            onSetSenamVideo = {},
            onSubmitTalim = { _, _, _ -> },
        )
    }
}

@Preview(showBackground = true, name = "Home · Pembiasaan done")
@Composable
private fun HomeContentPembiasaanDonePreview() {
    FridaySTMTheme {
        HomeContent(
            state = HomeUiState.Ready(
                user = previewStudent,
                phase = FridayPhase.PEMBIASAAN,
                activeActivity = Activity.LARKAM,
                record = AttendanceRecord(
                    uid = "u1",
                    date = "2026-08-14",
                    grade = 11,
                    pembiasaan = PembiasaanStamp(
                        activity = ActivityType.LARKAM,
                        checkedIn = true,
                        time = "06:45",
                    ),
                ),
                action = HomeAction.PembiasaanDone(Activity.LARKAM),
            ),
            submitStatus = SubmitStatus.Idle,
            senamState = previewSenamState,
            senamSetStatus = SubmitStatus.Idle,
            talimState = previewTalimState,
            talimSubmitStatus = SubmitStatus.Idle,
            onPembiasaanCheckIn = {},
            onCheckOut = {},
            onSetSenamVideo = {},
            onSubmitTalim = { _, _, _ -> },
        )
    }
}
