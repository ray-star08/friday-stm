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
import com.gynda.fridaystm.ui.screen.HistoryScreen
import com.gynda.fridaystm.ui.screen.HomeScreen
import com.gynda.fridaystm.ui.screen.LarkamTrackingScreen
import com.gynda.fridaystm.ui.screen.LoginScreen
import com.gynda.fridaystm.ui.screen.ProfileScreen
import com.gynda.fridaystm.ui.screen.SplashScreen
import com.gynda.fridaystm.util.FusedLocationProvider
import com.gynda.fridaystm.viewmodel.CameraViewModel
import com.gynda.fridaystm.viewmodel.HistoryViewModel
import com.gynda.fridaystm.viewmodel.HomeViewModel
import com.gynda.fridaystm.viewmodel.LarkamViewModel
import com.gynda.fridaystm.viewmodel.LoginViewModel
import com.gynda.fridaystm.viewmodel.ProfileViewModel
import com.gynda.fridaystm.viewmodel.SenamViewModel
import com.gynda.fridaystm.viewmodel.TalimViewModel

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

    NavHost(
        navController = navController,
        startDestination = Routes.Splash.route,
        modifier = modifier,
    ) {
        composable(Routes.Splash.route) {
            SplashScreen(
                onSplashFinished = {
                    navController.navigate(Routes.Login.route) {
                        popUpTo(Routes.Splash.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.Login.route) {
            LoginScreen(
                viewModel = viewModel(factory = LoginViewModel.factory()),
                onLoginSuccess = {
                    navController.navigate(Routes.Home.route) {
                        popUpTo(Routes.Login.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.Home.route) {
            // HomeViewModel is scoped locally to the Home destination, so it does
            // not initialize while Splash is showing.
            val homeViewModel: HomeViewModel = viewModel(
                factory = remember(locationProvider) { HomeViewModel.factory(locationProvider) }
            )
            val senamViewModel: SenamViewModel = viewModel(factory = SenamViewModel.factory())
            val talimViewModel: TalimViewModel = viewModel(factory = TalimViewModel.factory())
            HomeScreen(
                viewModel = homeViewModel,
                senamViewModel = senamViewModel,
                talimViewModel = talimViewModel,
                onNavigateToCheckIn = {
                    if (homeViewModel.currentCheckInContext() != null) {
                        navController.navigate(Routes.Camera.route)
                    }
                },
                onOpenLarkam = { navController.navigate(Routes.Larkam.route) },
                onNavigateToLogin = {
                    navController.navigate(Routes.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
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
            LarkamTrackingScreen(
                viewModel = viewModel(
                    factory = remember(locationProvider) { LarkamViewModel.factory(locationProvider) },
                ),
                onDone = { navController.popBackStack(Routes.Home.route, inclusive = false) },
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
    }
}
