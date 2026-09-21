package com.gynda.fridaystm.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.gynda.fridaystm.ui.screen.CameraCaptureScreen
import com.gynda.fridaystm.ui.screen.ExportReportScreen
import com.gynda.fridaystm.ui.screen.HistoryScreen
import com.gynda.fridaystm.ui.screen.HomeScreen
import com.gynda.fridaystm.ui.screen.IzinApprovalScreen
import com.gynda.fridaystm.ui.screen.LarkamTrackingScreen
import com.gynda.fridaystm.ui.screen.LoginScreen
import com.gynda.fridaystm.ui.screen.PengajuanIzinScreen
import com.gynda.fridaystm.ui.screen.PresensiCameraScreen
import com.gynda.fridaystm.ui.screen.PresensiHistoryScreen
import com.gynda.fridaystm.ui.screen.ProfileScreen
import com.gynda.fridaystm.ui.screen.SplashScreen
import com.gynda.fridaystm.ui.screen.TeacherDashboardScreen
import com.gynda.fridaystm.BuildConfig
import com.gynda.fridaystm.data.local.AppDatabase
import com.gynda.fridaystm.data.local.RoomPendingPresensiStore
import com.gynda.fridaystm.util.DebugTimeProvider
import com.gynda.fridaystm.util.FusedLocationProvider
import com.gynda.fridaystm.util.SystemTimeProvider
import com.gynda.fridaystm.util.WorkManagerPresensiSyncScheduler
import com.gynda.fridaystm.viewmodel.CameraViewModel
import com.gynda.fridaystm.viewmodel.HistoryViewModel
import com.gynda.fridaystm.viewmodel.HomeViewModel
import com.gynda.fridaystm.viewmodel.LarkamViewModel
import com.gynda.fridaystm.viewmodel.LoginViewModel
import com.gynda.fridaystm.viewmodel.MainViewModel
import com.gynda.fridaystm.viewmodel.MainNavTarget
import com.gynda.fridaystm.viewmodel.OfflineQueueViewModel
import com.gynda.fridaystm.viewmodel.ProfileViewModel
import com.gynda.fridaystm.viewmodel.SenamViewModel
import com.gynda.fridaystm.viewmodel.TalimViewModel
import com.gynda.fridaystm.viewmodel.TeacherDashboardViewModel

/**
 * The app's navigation graph. Each screen receives only the callbacks it needs;
 * the NavHost owns all `navController` interactions so screens stay navigation-agnostic.
 *
 * [HomeViewModel] is hoisted here (scoped to the NavHost owner) so the Home and
 * Camera routes share one instance — the selfie URL produced by the camera flow
 * flows back into the same ViewModel that owns the check-in state.
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // LocationProvider is shared across the graph.
    val locationProvider = remember(context) { FusedLocationProvider(context) }
    // Debug time controller — only in DEBUG builds, reuses the same instance across recompositions.
    val debugTimeProvider: DebugTimeProvider? = remember {
        if (BuildConfig.DEBUG) DebugTimeProvider() else null
    }
    val timeProvider = debugTimeProvider ?: SystemTimeProvider()

    NavHost(
        navController = navController,
        startDestination = Routes.Splash.route,
        modifier = modifier,
    ) {
        composable(Routes.Splash.route) {
            val mainViewModel: MainViewModel = viewModel(factory = MainViewModel.factory())
            SplashScreen(
                viewModel = mainViewModel,
                onNavigateToLogin = {
                    navController.navigate(Routes.Login.route) {
                        popUpTo(Routes.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToStudentHome = {
                    navController.navigate(Routes.Home.route) {
                        popUpTo(Routes.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToTeacherDashboard = {
                    navController.navigate(Routes.TeacherDashboard.route) {
                        popUpTo(Routes.Splash.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.Login.route) {
            val mainViewModel: MainViewModel = viewModel(factory = MainViewModel.factory())
            LoginScreen(
                viewModel = viewModel(factory = LoginViewModel.factory()),
                mainViewModel = mainViewModel,
                onLoginSuccessStudent = {
                    navController.navigate(Routes.Home.route) {
                        popUpTo(Routes.Login.route) { inclusive = true }
                    }
                },
                onLoginSuccessTeacher = {
                    navController.navigate(Routes.TeacherDashboard.route) {
                        popUpTo(Routes.Login.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.Home.route) {
            // HomeViewModel is scoped locally to the Home destination, so it does
            // not initialize while Splash is showing.
            val homeViewModel: HomeViewModel = viewModel(
                factory = remember(locationProvider, timeProvider) {
                    HomeViewModel.factory(locationProvider, timeProvider)
                }
            )
            val senamViewModel: SenamViewModel = viewModel(factory = SenamViewModel.factory())
            val talimViewModel: TalimViewModel = viewModel(factory = TalimViewModel.factory())
            // Offline-queue banner source: Room count observed per signed-in user.
            val offlineQueueViewModel: OfflineQueueViewModel = viewModel(
                factory = remember(context) {
                    val app = context.applicationContext
                    OfflineQueueViewModel.factory(
                        queue = RoomPendingPresensiStore(AppDatabase.get(app).pendingPresensiDao()),
                        syncScheduler = WorkManagerPresensiSyncScheduler(app),
                    )
                }
            )
            HomeScreen(
                viewModel = homeViewModel,
                senamViewModel = senamViewModel,
                talimViewModel = talimViewModel,
                offlineQueueViewModel = offlineQueueViewModel,
                onNavigateToCheckIn = {
                    if (homeViewModel.currentCheckInContext() != null) {
                        navController.navigate(Routes.Camera.route)
                    }
                },
                onNavigateToPresensiCamera = {
                    navController.navigate(Routes.PresensiCamera.route)
                },
                onOpenPresensiHistory = {
                    navController.navigate(Routes.PresensiHistory.route)
                },
                onOpenLarkam = { navController.navigate(Routes.Larkam.route) },
                onOpenPengajuanIzin = { navController.navigate(Routes.PengajuanIzin.route) },
                onNavigateToLogin = {
                    navController.navigate(Routes.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.PresensiCamera.route) {
            PresensiCameraScreen(
                onSuccessNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.PresensiHistory.route) {
            PresensiHistoryScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.PengajuanIzin.route) {
            PengajuanIzinScreen(
                onBack = { navController.popBackStack() },
                // Kembali ke Dashboard setelah submit berhasil (SUCCESS state).
                onSubmitSuccess = {
                    navController.popBackStack(Routes.Home.route, inclusive = false)
                },
            )
        }

        composable(Routes.Camera.route) { backStackEntry ->
            // Retrieve the SAME HomeViewModel instance from the Home backstack entry.
            // This is safe because Camera is navigated to from Home (M4.2).
            val homeEntry = remember(backStackEntry) { 
                navController.getBackStackEntry(Routes.Home.route) 
            }
            val homeViewModel: HomeViewModel = viewModel(homeEntry)

            val ctx = homeViewModel.currentCheckInContext()
            if (ctx == null) {
                navController.popBackStack(Routes.Home.route, inclusive = false)
            } else {
                CameraCaptureScreen(
                    uid = ctx.uid,
                    date = ctx.date,
                    phase = ctx.phase,
                    viewModel = viewModel(factory = CameraViewModel.factory()),
                    onSelfieReady = { secureUrl ->
                        homeViewModel.onSelfieReady(secureUrl)
                        navController.popBackStack(Routes.Home.route, inclusive = false)
                    },
                )
            }
        }

        composable(Routes.Larkam.route) {
            val larkamViewModel: com.gynda.fridaystm.viewmodel.LarkamViewModel = viewModel(
                factory = remember(locationProvider) { com.gynda.fridaystm.viewmodel.LarkamViewModel.factory(locationProvider) },
            )
            LarkamTrackingScreen(
                viewModel = larkamViewModel,
                onDone = { navController.popBackStack(Routes.Home.route, inclusive = false) },
                onFinishSelfie = {
                    val state = larkamViewModel.uiState.value
                    com.gynda.fridaystm.util.LarkamPayloadHolder.set(
                        distanceKm = (state.distanceMeters / 1000.0).toFloat(),
                        durationSeconds = state.elapsedSec,
                        durationFormatted = state.timerFormatted,
                        route = state.path.map { mapOf("lat" to it.lat, "lng" to it.lng) }
                    )
                    navController.navigate(Routes.PresensiCamera.route)
                }
            )
        }

        composable(Routes.History.route) {
            HistoryScreen(viewModel = viewModel(factory = HistoryViewModel.factory()))
        }

        composable(Routes.Profile.route) {
            ProfileScreen(
                viewModel = viewModel(factory = ProfileViewModel.factory()),
                onLogout = {
                    // Clear the whole back stack and return to Login.
                    navController.navigate(Routes.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.TeacherDashboard.route) {
            TeacherDashboardScreen(
                viewModel = viewModel(factory = TeacherDashboardViewModel.factory()),
                onOpenIzinApproval = { navController.navigate(Routes.IzinApproval.route) },
                onOpenExportReport = { navController.navigate(Routes.ExportReport.route) },
                onLogout = {
                    navController.navigate(Routes.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.IzinApproval.route) {
            IzinApprovalScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.ExportReport.route) {
            ExportReportScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
