package com.gynda.fridaystm.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.gynda.fridaystm.ui.navigation.AppNavHost
import com.gynda.fridaystm.ui.navigation.FridayBottomBar
import com.gynda.fridaystm.ui.navigation.Routes
import com.gynda.fridaystm.ui.navigation.topLevelRoutes

/**
 * Root composable for the app. Owns the [rememberNavController] and the app
 * shell (Scaffold + bottom bar), delegating actual screen content to [AppNavHost].
 * The bottom bar only appears on top-level destinations (Home/Riwayat/Profil).
 */
@Composable
fun FridayStmApp(
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in topLevelRoutes

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                FridayBottomBar(
                    currentRoute = currentRoute,
                    onSelect = { route ->
                        navController.navigate(route) {
                            // Anchor to Home (the top-level base), NOT the graph start
                            // destination — that is Splash, which is popped after login
                            // and would no longer be on the back stack.
                            popUpTo(Routes.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        AppNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }
}
