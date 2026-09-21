package com.gynda.fridaystm.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

/**
 * Single source of truth for spacing / radius / elevation.
 * Use these instead of hard-coded `16.dp` scattered in screens.
 * Phone: screen padding 16dp, tablet (600dp+) 24dp via LocalConfiguration.
 */
object Spacing {
    val s4 = 4.dp
    val s8 = 8.dp
    val s12 = 12.dp
    val s16 = 16.dp
    val s20 = 20.dp
    val s24 = 24.dp
    val s32 = 32.dp
}

object Radius {
    val xs = 8.dp
    val s = 12.dp
    val m = 16.dp
    val l = 20.dp
    val xl = 24.dp
    val xxl = 28.dp
    val pill = 50.dp // fully pill
}

object Elevation {
    val none = 0.dp
    val card = 1.dp
    val dialog = 3.dp
}

val ScreenPadding = PaddingValues(horizontal = Spacing.s16, vertical = Spacing.s16)
val ScreenPaddingTablet = PaddingValues(horizontal = Spacing.s24, vertical = Spacing.s24)
