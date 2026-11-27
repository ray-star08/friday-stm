package com.gynda.fridaystm.ui

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.gynda.fridaystm.MainActivity
import org.junit.Assume
import org.junit.Rule
import org.junit.Test

/**
 * Demo helper: signs in on the device so a phone ends up logged in without
 * manual typing (adb input injection is blocked on MIUI). Compose test actions
 * go through in-process semantics, not input injection.
 *
 * Run with credentials as instrumentation arguments, e.g.:
 *   gradlew :app:connectedDebugAndroidTest
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.gynda.fridaystm.ui.DemoLoginSmokeTest
 *     -Pandroid.testInstrumentationRunnerArguments.demoEmail=siswa@fridaystm.app
 *     -Pandroid.testInstrumentationRunnerArguments.demoPassword=siswa123
 *
 * Skips itself (assumption failure) when demoEmail is absent, so regular
 * connected-test runs are unaffected. Idempotent: passes when already signed in.
 */
class DemoLoginSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun demoCredentialsReachHome() {
        val args = InstrumentationRegistry.getArguments()
        val email = args.getString("demoEmail")
        Assume.assumeTrue("demoEmail not provided — skipping demo login", email != null)
        val password = args.getString("demoPassword") ?: ""

        // Splash resolves to Login (signed out) or Home (already signed in).
        // runCatching: while a system permission dialog is up, the hierarchy
        // query throws — keep polling instead of failing.
        compose.waitUntil(60_000) {
            runCatching {
                compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size >= 2 ||
                    compose.onAllNodesWithText("Beranda").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }

        val fields = compose.onAllNodes(hasSetTextAction())
        if (runCatching { fields.fetchSemanticsNodes().size }.getOrDefault(0) >= 2) {
            fields[0].performTextInput(email!!)
            fields[1].performTextInput(password)
            compose.onNodeWithText("Masuk").performClick()
        }

        compose.waitUntil(120_000) {
            runCatching {
                compose.onAllNodesWithText("Beranda").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
    }
}
