package com.gynda.fridaystm.ui

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import com.gynda.fridaystm.data.model.AttendanceRecord
import com.gynda.fridaystm.data.model.AttendanceDayProjector
import com.gynda.fridaystm.data.model.PembiasaanStamp
import com.gynda.fridaystm.data.model.ProfileStats
import com.gynda.fridaystm.data.model.User
import com.gynda.fridaystm.ui.screen.HistoryContent
import com.gynda.fridaystm.ui.screen.LoginContent
import com.gynda.fridaystm.ui.screen.ProfileContent
import com.gynda.fridaystm.ui.screen.TeacherDashboardContent
import com.gynda.fridaystm.ui.theme.FridaySTMTheme
import com.gynda.fridaystm.viewmodel.HistoryUiState
import com.gynda.fridaystm.viewmodel.LoginUiState
import com.gynda.fridaystm.viewmodel.TeacherDashboardUiState
import com.gynda.fridaystm.viewmodel.UserProfileUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Synthetic, offline-only content tests; never starts Firebase or a camera. */
class RedesignUiTest {
    @get:Rule val compose = createComposeRule()

    private val student = User(uid = "ui-fixture", nama = "Siswa Contoh", nis = "DEMO-001", kelas = "XI RPL 1", grade = 11)

    @Composable
    private fun TestTheme(darkTheme: Boolean? = null, content: @Composable () -> Unit) {
        val args = InstrumentationRegistry.getArguments()
        val fontScale = args.getString("fontScale")?.toFloat() ?: 1f
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            FridaySTMTheme(darkTheme = darkTheme ?: (args.getString("darkTheme") == "true"), content = content)
        }
    }

    private fun saveScreenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.getExternalFilesDir(null), "ui-evidence").apply { mkdirs() }
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(dir, "$name-${InstrumentationRegistry.getArguments().getString("evidenceSuffix", "normal")}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun loginLight_keepsSubmitCallback() {
        var submitted = 0
        compose.setContent {
            TestTheme(darkTheme = false) {
                Surface { LoginContent(LoginUiState(email = "demo@example.invalid", password = "fixture-only"), {}, {}, { submitted++ }) }
            }
        }
        compose.onNodeWithText("Masuk akun sekolah").assertIsDisplayed()
        compose.onNodeWithText("Masuk", ignoreCase = true).performScrollTo().performClick()
        assertEquals(1, submitted)
        saveScreenshot("login-light")
    }

    @Test fun loginDark_rendersBrandAndForm() {
        compose.setContent {
            TestTheme(darkTheme = true) {
                Surface { LoginContent(LoginUiState(), {}, {}, {}) }
            }
        }
        compose.onNodeWithText("Masuk akun sekolah").assertIsDisplayed()
        saveScreenshot("login-dark")
    }

    @Test fun history_showsReadableHeadingAndEvidence() {
        compose.setContent {
            TestTheme {
                Surface {
                    HistoryContent(HistoryUiState.Success(AttendanceDayProjector.merge(listOf(AttendanceRecord(uid = "ui-fixture", date = "2026-09-18", grade = 11, pembiasaan = PembiasaanStamp(activity = "talim", checkedIn = true, time = "06:45", lat = -6.876, lng = 107.541, valid = true))), emptyList())))
                }
            }
        }
        compose.onNodeWithText("Riwayat aktivitas").assertIsDisplayed()
        saveScreenshot("history-light")
    }

    @Test fun history_compactViewportKeepsRecordsReachable() {
        compose.setContent {
            TestTheme {
                Surface(modifier = androidx.compose.ui.Modifier.height(160.dp)) {
                    HistoryContent(HistoryUiState.Success(AttendanceDayProjector.merge(listOf(AttendanceRecord(uid = "ui-fixture", date = "2026-09-18")), emptyList())))
                }
            }
        }
        compose.onNode(androidx.compose.ui.test.hasScrollAction()).assertHeightIsAtLeast(100.dp)
        compose.onNode(androidx.compose.ui.test.hasScrollAction())
            .performScrollToNode(androidx.compose.ui.test.hasText("2026-09-18"))
        compose.onNodeWithText("2026-09-18").assertIsDisplayed()
    }

    @Test fun profile_keepsIdentityVisible() {
        compose.setContent {
            TestTheme {
                Surface { ProfileContent(UserProfileUiState.Success(student, "demo@example.invalid", ProfileStats()), {}) }
            }
        }
        compose.onNodeWithText("Akun saya").assertIsDisplayed()
        compose.onNodeWithText("Siswa Contoh").assertIsDisplayed()
        saveScreenshot("profile-light")
    }

    @Test fun profile_logoutRequiresConfirmation() {
        var loggedOut = 0
        compose.setContent {
            TestTheme {
                Surface { ProfileContent(UserProfileUiState.Success(student, "demo@example.invalid", ProfileStats()), { loggedOut++ }) }
            }
        }
        compose.onNodeWithText("Keluar").performScrollTo().performClick()
        assertEquals(0, loggedOut)
        compose.onNodeWithText("Batal").performClick()
        assertEquals(0, loggedOut)
        compose.onNodeWithText("Keluar").performScrollTo().performClick()
        compose.onNodeWithText("Ya, Keluar").performClick()
        assertEquals(1, loggedOut)
    }

    @Test fun navigation_forwardsDestination() {
        var destination = ""
        compose.setContent {
            TestTheme {
                com.gynda.fridaystm.ui.navigation.FridayBottomBar("home", { destination = it })
            }
        }
        compose.onNodeWithText("Riwayat").performClick()
        assertEquals("history", destination)
        compose.onNodeWithText("Profil").performClick()
        assertEquals("profile", destination)
    }

    @Test fun login_emptyFormCannotSubmit() {
        compose.setContent { TestTheme { Surface { LoginContent(LoginUiState(), {}, {}, {}) } } }
        compose.onNodeWithText("Masuk").performScrollTo().assertIsNotEnabled()
    }

    @Test fun home_showsMenuAndKeepsCheckoutAction() {
        var checkedOut = 0
        compose.setContent {
            TestTheme {
                Surface {
                    com.gynda.fridaystm.ui.screen.HomeContent(
                        state = com.gynda.fridaystm.viewmodel.HomeUiState.Ready(
                            user = student,
                            phase = com.gynda.fridaystm.domain.FridayPhase.CHECKOUT,
                            activeActivity = com.gynda.fridaystm.domain.Activity.LARKAM,
                            record = AttendanceRecord(uid = student.uid, date = "2026-09-18", pembiasaan = PembiasaanStamp(checkedIn = true)),
                            action = com.gynda.fridaystm.viewmodel.HomeAction.CheckOut,
                        ),
                        submitStatus = com.gynda.fridaystm.viewmodel.SubmitStatus.Idle,
                        senamState = com.gynda.fridaystm.viewmodel.SenamUiState(weekId = "2026-W38"),
                        senamSetStatus = com.gynda.fridaystm.viewmodel.SubmitStatus.Idle,
                        talimState = com.gynda.fridaystm.viewmodel.TalimUiState(date = "2026-09-18"),
                        talimSubmitStatus = com.gynda.fridaystm.viewmodel.SubmitStatus.Idle,
                        onPembiasaanCheckIn = {}, onCheckOut = { checkedOut++ },
                        onSetSenamVideo = {}, onSubmitTalim = { _, _, _ -> },
                    )
                }
            }
        }
        compose.onNodeWithText("JUMAT, LEBIH TERATUR").assertIsDisplayed()
        saveScreenshot("home-light")
        compose.onNodeWithText("Check-out Sekarang").performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, checkedOut)
        compose.onNodeWithText("Menu siswa").performScrollTo().assertIsDisplayed()
    }

    @Test fun teacher_keepsFilterAndActions() {
        var approval = 0
        compose.setContent {
            TestTheme {
                Surface {
                    TeacherDashboardContent(
                        TeacherDashboardUiState(selectedClass = "XI RPL 1", selectedDate = "2026-09-18", isLoading = false),
                        {}, {}, onOpenIzinApproval = { approval++ }, onOpenExportReport = {},
                    )
                }
            }
        }
        compose.onNodeWithText("Pantau kelas").assertIsDisplayed()
        compose.onNodeWithText("Persetujuan izin").performScrollTo().performClick()
        assertEquals(1, approval)
        saveScreenshot("teacher-light")
    }
}
