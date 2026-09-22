package com.gynda.fridaystm.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import org.junit.After
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.gynda.fridaystm.R
import com.gynda.fridaystm.data.model.AttendanceDay
import com.gynda.fridaystm.data.model.AttendanceDayProjector
import com.gynda.fridaystm.data.model.AttendanceDayStatus
import com.gynda.fridaystm.data.model.AttendanceRecord
import com.gynda.fridaystm.data.model.CheckoutStamp
import com.gynda.fridaystm.data.model.PembiasaanStamp
import com.gynda.fridaystm.data.model.PresensiRecord
import com.gynda.fridaystm.data.model.ProfileStats
import com.gynda.fridaystm.data.model.User
import com.gynda.fridaystm.ui.screen.HistoryContent
import com.gynda.fridaystm.ui.screen.ProfileContent
import com.gynda.fridaystm.ui.theme.FridaySTMTheme
import com.gynda.fridaystm.viewmodel.HistoryUiState
import com.gynda.fridaystm.viewmodel.UserProfileUiState
import org.junit.Rule
import org.junit.Test

/** Offline fixtures only: no ViewModels, auth, Firebase, or remote image URLs. */
class AttendanceStudentUiTest {
    @get:Rule val compose = createComposeRule()

    @Composable
    private fun TestTheme(content: @Composable () -> Unit) {
        val args = InstrumentationRegistry.getArguments()
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, args.getString("fontScale", "1.0").toFloat())) {
            FridaySTMTheme(darkTheme = args.getString("darkTheme") == "true", content = content)
        }
    }

    @After fun captureEvidence() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "ui-evidence").apply { mkdirs() }
        val allRoots = compose.onAllNodes(isRoot())
        val roots = allRoots[allRoots.fetchSemanticsNodes().lastIndex]
        File(directory, "student-${System.nanoTime()}.png").outputStream().use {
            roots.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun legacy(userId: String = "first") = AttendanceDayProjector.merge(
        emptyList(), listOf(PresensiRecord(id = "photo", userId = userId, timestamp = "2026-09-18T06:43:00")),
    ).single()

    @Test fun legacy_hasRealTimeAndNoInventedPhaseOrVerification() {
        val day = legacy()
        compose.setContent { TestTheme { Surface { HistoryContent(HistoryUiState.Success(listOf(day))) } } }
        compose.onNodeWithText("Data lama").assertIsDisplayed()
        compose.onNodeWithText("06:43", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Valid", ignoreCase = true).assertDoesNotExist()
        compose.onNodeWithText("Check-out", substring = true).assertDoesNotExist()
        compose.onNodeWithText("2026-09-18").performClick()
        compose.onNodeWithTag("legacy-attendance-detail").assertIsDisplayed()
        compose.onNode(hasText("Fase dan penyelesaian tidak tersedia pada data lama.") and hasAnyAncestor(hasTestTag("legacy-attendance-detail"))).assertIsDisplayed()
        compose.onNodeWithText("Valid", ignoreCase = true).assertDoesNotExist()
    }

    @Test fun canonical_keepsPhaseEvidenceAndDetail() {
        val record = AttendanceRecord(uid = "first", date = "2026-09-18", status = "complete",
            pembiasaan = PembiasaanStamp(checkedIn = true, valid = true, time = "06:44", activity = "talim"),
            checkout = CheckoutStamp(checkedOut = true, time = "08:12"))
        val days = AttendanceDayProjector.merge(listOf(record), emptyList())
        compose.setContent { TestTheme { Surface { HistoryContent(HistoryUiState.Success(days)) } } }
        compose.onNodeWithText("Lengkap").assertIsDisplayed()
        compose.onNodeWithText("06:44", substring = true).assertIsDisplayed()
        compose.onNodeWithText("2026-09-18").performClick()
        compose.onNodeWithTag("canonical-attendance-detail").assertIsDisplayed()
        compose.onNode(hasText("08:12", substring = true) and hasAnyAncestor(hasTestTag("canonical-attendance-detail"))).assertIsDisplayed()
    }

    @Test fun lifecycleBadges_distinguishPartialAndReview() {
        val days = listOf(
            AttendanceDay("first", "2026-09-18", AttendanceDayStatus.PARTIAL),
            AttendanceDay("first", "2026-09-11", AttendanceDayStatus.NEEDS_REVIEW),
        )
        compose.setContent { TestTheme { Surface { HistoryContent(HistoryUiState.Success(days)) } } }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Sebagian"))
        compose.onNodeWithText("Sebagian").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Perlu ditinjau"))
        compose.onNodeWithText("Perlu ditinjau").assertIsDisplayed()
    }

    @Test fun historyStateChange_clearsOpenDetails() {
        val state = mutableStateOf<HistoryUiState>(HistoryUiState.Success(listOf(legacy())))
        compose.setContent { TestTheme { Surface { HistoryContent(state.value) } } }
        compose.onNodeWithText("2026-09-18").performClick()
        compose.onNodeWithTag("legacy-attendance-detail").assertIsDisplayed()
        compose.runOnIdle { state.value = HistoryUiState.Loading }
        compose.onNodeWithTag("legacy-attendance-detail").assertDoesNotExist()
        compose.runOnIdle { state.value = HistoryUiState.Success(listOf(legacy("second"))) }
        compose.onNodeWithTag("legacy-attendance-detail").assertDoesNotExist()
    }

    @Test fun historyError_isNotPresentedAsEmpty() {
        compose.setContent { TestTheme { Surface { HistoryContent(HistoryUiState.Error(R.string.history_error_generic)) } } }
        compose.onNodeWithText("Gagal memuat riwayat. Coba lagi.").assertIsDisplayed()
        compose.onNodeWithText("Belum ada riwayat", substring = true).assertDoesNotExist()
    }

    @Test fun profile_labelsRecordedDaysAndApplicationsExplicitly() {
        compose.setContent { TestTheme { Surface {
            ProfileContent(UserProfileUiState.Success(User(uid = "first", nama = "Contoh"), "demo@example.invalid", ProfileStats(presensiCount = 3, izinCount = 2)), {})
        } } }
        compose.onNodeWithText("Tercatat").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Pengajuan izin").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Hadir").assertDoesNotExist()
    }
}
