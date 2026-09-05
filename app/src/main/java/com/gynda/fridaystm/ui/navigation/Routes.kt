package com.gynda.fridaystm.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Single source of truth for navigation destinations. Screens and the NavHost
 * reference `Routes.Home.route` etc. instead of raw strings, per SKILL.md
 * ("no magic strings at call sites").
 *
 * Note: this string-based sealed hierarchy keeps setup lean. If we later add
 * arguments, migrate to Navigation-Compose's type-safe (`@Serializable`) API.
 */
sealed interface Routes {
    val route: String

    data object Splash : Routes { override val route = "splash" }
    data object Login : Routes { override val route = "login" }
    data object Home : Routes { override val route = "home" }
    data object History : Routes { override val route = "history" }
    data object Profile : Routes { override val route = "profile" }

    /** Selfie capture for the current phase's check-in (M4.3). */
    data object Camera : Routes { override val route = "camera" }

    /** Larkam run tracker — live map + duration/distance, logs to Firestore (Activity 4). */
    data object Larkam : Routes { override val route = "larkam" }
}

/**
 * Destinations shown in the bottom navigation bar (ala Ruangguru).
 * The bottom bar is hidden on non-top-level routes (Splash, Login).
 */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME(Routes.Home.route, "Home", Icons.Default.Home),
    HISTORY(Routes.History.route, "Riwayat", Icons.Default.DateRange),
    PROFILE(Routes.Profile.route, "Profil", Icons.Default.Person),
}

/** Routes that should display the bottom navigation bar. */
val topLevelRoutes: Set<String> = TopLevelDestination.entries.map { it.route }.toSet()
