package com.gynda.fridaystm.ui

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.gynda.fridaystm.data.model.*
import com.gynda.fridaystm.ui.screen.TeacherDashboardContent
import com.gynda.fridaystm.ui.theme.FridaySTMTheme
import com.gynda.fridaystm.viewmodel.TeacherDashboardUiState
import org.junit.Rule
import org.junit.Test
import java.io.File

class AttendanceTeacherUiTest {
    @get:Rule val compose = createComposeRule()
    private val user = User(uid = "fixture", nama = "Siswa Fixture", kelas = "XI A", nis = "DEMO")

    private fun render(state: () -> TeacherDashboardUiState) {
        compose.setContent {
            val args = InstrumentationRegistry.getArguments()
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, args.getString("fontScale", "1.0").toFloat())) {
                FridaySTMTheme(darkTheme = args.getString("darkTheme") == "true") {
                    Surface { TeacherDashboardContent(state(), {}, {}) }
                }
            }
        }
    }
    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.getExternalFilesDir(null), "ui-evidence").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun lifecycleAndReviewTotalsAreVisible() {
        val day = AttendanceDay(user.uid, "2026-09-18", AttendanceDayStatus.PARTIAL)
        val items = buildDayAttendanceList(listOf(user), listOf(day), emptyList(), emptyList())
        render { TeacherDashboardUiState(selectedClass = "XI A", isLoading = false, selectedDate = day.date,
            students = items, stats = calculateDayTeacherStats(items)) }
        compose.onNodeWithText("Tercatat").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Perlu ditinjau: 0 siswa").performScrollTo().assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Belum checkout"))
        compose.onNodeWithText("Belum checkout").assertIsDisplayed()
        capture("teacher-lifecycle")
    }

    @Test fun errorDoesNotShowSuccessfulZeroSummary() {
        render { TeacherDashboardUiState(isLoading = false, error = "Koneksi gagal") }
        compose.onNodeWithText("Tercatat").assertDoesNotExist()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Koneksi gagal", substring = true))
        compose.onNodeWithText("Koneksi gagal", substring = true).assertIsDisplayed()
        capture("teacher-error")
    }

    @Test fun accountResetClearsOpenStudentDetail() {
        val record = AttendanceRecord(uid = user.uid, date = "2026-09-18",
            pembiasaan = PembiasaanStamp(checkedIn = true, valid = true, time = "06:45"),
            checkout = CheckoutStamp(checkedOut = true, time = "08:10"), status = "complete")
        val days = AttendanceDayProjector.merge(listOf(record), emptyList())
        val items = buildDayAttendanceList(listOf(user), days, emptyList(), emptyList())
        var state by mutableStateOf(TeacherDashboardUiState(isLoading = false, students = items, stats = calculateDayTeacherStats(items)))
        render { state }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(user.nama))
        compose.onNodeWithText(user.nama).performClick()
        compose.onNodeWithText("Check-out: 08:10").assertExists()
        compose.runOnIdle { state = TeacherDashboardUiState(isLoading = true) }
        compose.onAllNodesWithText(user.nama).assertCountEquals(0)
        capture("teacher-account-reset")
    }
}
