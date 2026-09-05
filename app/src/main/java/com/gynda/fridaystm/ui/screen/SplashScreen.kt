package com.gynda.fridaystm.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.gynda.fridaystm.ui.component.PlaceholderContent
import com.gynda.fridaystm.ui.theme.FridaySTMTheme
import kotlinx.coroutines.delay

/**
 * Entry screen. For now it simply waits briefly then hands off. In Milestone 3
 * this is where we will branch to Home vs Login based on the auth state.
 *
 * @param onSplashFinished invoked once the splash delay elapses.
 */
@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        delay(SPLASH_DELAY_MILLIS)
        onSplashFinished()
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
