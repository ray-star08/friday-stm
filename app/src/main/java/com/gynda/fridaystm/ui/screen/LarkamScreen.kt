package com.gynda.fridaystm.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gynda.fridaystm.viewmodel.LarkamViewModel

/**
 * LarkamScreen — spec alias for [LarkamTrackingScreen].
 * Uses MapView via osmdroid (Polyline + marker) as MapView Compose alternative
 * to Google Maps, with live dashboard (KM 2 desimal, HH:MM:SS, Pace).
 */
@Composable
fun LarkamScreen(
    viewModel: LarkamViewModel,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onFinishSelfie: (() -> Unit)? = null,
) {
    LarkamTrackingScreen(
        viewModel = viewModel,
        onDone = onDone,
        onFinishSelfie = onFinishSelfie,
        modifier = modifier
    )
}
