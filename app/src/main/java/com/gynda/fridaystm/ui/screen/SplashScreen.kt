package com.gynda.fridaystm.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gynda.fridaystm.ui.component.PlaceholderContent
import com.gynda.fridaystm.ui.theme.FridaySTMTheme
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
        PlaceholderContent(
            title = "Friday STM",
            subtitle = "Disiplin Jumat pagi, satu ketukan.",
            modifier = modifier,
        )
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

    PlaceholderContent(
        title = "Friday STM",
        subtitle = "Disiplin Jumat pagi, satu ketukan.",
        modifier = modifier,
    )
}

private const val SPLASH_DELAY_MILLIS = 1_200L

@Preview(showBackground = true)
@Composable
private fun SplashScreenPreview() {
    FridaySTMTheme {
        SplashScreen(onSplashFinished = {})
    }
}
