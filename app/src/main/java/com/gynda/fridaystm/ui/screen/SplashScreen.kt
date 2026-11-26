package com.gynda.fridaystm.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gynda.fridaystm.R
import com.gynda.fridaystm.ui.theme.FridaySTMTheme
import com.gynda.fridaystm.ui.theme.Spacing
import com.gynda.fridaystm.viewmodel.MainNavTarget
import com.gynda.fridaystm.viewmodel.MainViewModel
import kotlinx.coroutines.delay

/**
 * Entry screen — role-aware splash (spec 1: role-based routing).
 *
 * Waits [SPLASH_DELAY_MILLIS] then branches based on [MainViewModel.navTarget]:
 *  - GURU/ADMIN → TeacherDashboard
 *  - SISWA      → Home (student dashboard)
 *  - signed-out / error → Login
 *
 * Back-compat: when no [viewModel] is provided (preview), simply calls
 * [onSplashFinished] after the delay to preserve the old preview contract.
 */
@Composable
fun SplashScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel? = null,
    onSplashFinished: (() -> Unit)? = null,
    onNavigateToLogin: (() -> Unit)? = null,
    onNavigateToStudentHome: (() -> Unit)? = null,
    onNavigateToTeacherDashboard: (() -> Unit)? = null,
) {
    // Preview / legacy path: no ViewModel → old single-callback behaviour.
    if (viewModel == null) {
        LaunchedEffect(Unit) {
            delay(SPLASH_DELAY_MILLIS)
            onSplashFinished?.invoke()
        }
        SplashBrand(modifier)
        return
    }

    val navTarget by viewModel.navTarget.collectAsStateWithLifecycle()

    LaunchedEffect(navTarget) {
        // Keep splash visible at least SPLASH_DELAY_MILLIS, but don't navigate
        // while still Loading (profile fetch in-flight).
        delay(SPLASH_DELAY_MILLIS)
        // If still Loading after delay, wait until it resolves (catch-all 2s).
        var waited = 0
        while (navTarget is MainNavTarget.Loading && waited < 2000) {
            delay(200)
            waited += 200
        }
        when (navTarget) {
            MainNavTarget.TeacherDashboard -> onNavigateToTeacherDashboard?.invoke()
                ?: onSplashFinished?.invoke()
            MainNavTarget.StudentHome -> onNavigateToStudentHome?.invoke()
                ?: onSplashFinished?.invoke()
            else -> onNavigateToLogin?.invoke()
                ?: onSplashFinished?.invoke()
        }
    }

    SplashBrand(modifier)
}

/** Splash: monogram forest-green + nama sekolah, tanpa aset jaringan. */
@Composable
private fun SplashBrand(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(Spacing.s24),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_friday_logo),
                contentDescription = stringResource(R.string.redesign_brand),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(96.dp),
            )
            Spacer(Modifier.height(Spacing.s16))
            Text(
                text = stringResource(R.string.redesign_brand),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(Spacing.s8))
            Text(
                text = stringResource(R.string.splash_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private const val SPLASH_DELAY_MILLIS = 1_200L

@Preview(showBackground = true)
@Composable
private fun SplashScreenPreview() {
    FridaySTMTheme {
        SplashScreen(onSplashFinished = {})
    }
}
