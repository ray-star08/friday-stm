package com.gynda.fridaystm.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.gynda.fridaystm.ui.screen.OfflineQueueBanner
import com.gynda.fridaystm.ui.theme.FridaySTMTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CaptureQueueUiTest {
    @get:Rule val compose = createComposeRule()
    @Composable private fun Fixture(content: @Composable () -> Unit) {
        val args = InstrumentationRegistry.getArguments()
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, args.getString("fontScale")?.toFloat() ?: 1f)) {
            FridaySTMTheme(darkTheme = args.getString("darkTheme") == "true") { Surface { content() } }
        }
    }
    @Test fun terminalRowsExplainActionAndDisableImpossibleSync() {
        var clicks = 0
        compose.setContent { Fixture { OfflineQueueBanner(2, { clicks++ }, needsAttentionCount = 2) } }
        compose.onNodeWithText("2 foto belum tersinkron; 2 perlu ditangani. Hubungi admin atau ambil ulang foto.").assertIsDisplayed()
        compose.onNodeWithText("Sinkronkan").assertIsNotEnabled()
        assertEquals(0, clicks)
    }
    @Test fun mixedQueueKeepsRetryAvailableForRetryableRows() {
        var clicks = 0
        compose.setContent { Fixture { OfflineQueueBanner(3, { clicks++ }, needsAttentionCount = 1) } }
        compose.onNodeWithText("Sinkronkan").assertIsEnabled().performClick()
        assertEquals(1, clicks)
    }
}
