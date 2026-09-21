package com.gynda.fridaystm.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.gynda.fridaystm.ui.theme.Radius
import com.gynda.fridaystm.ui.theme.Spacing

/**
 * M3 shimmer — base = surfaceVariant, highlight = surface.
 * Reuse via `Modifier.shimmer()` or the ready skeletons below.
 */
fun Modifier.shimmer(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerTranslate",
    )
    val brush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surfaceVariant,
        ),
        start = Offset(translate - 500f, 0f),
        end = Offset(translate, 0f),
    )
    background(brush)
}

@Composable
fun TeacherDashboardSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.s12)) {
        // 4 KPI cards 2x2
        repeat(2) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.s8)) {
                repeat(2) {
                    Card(
                        modifier = Modifier.weight(1f).height(84.dp),
                        shape = RoundedCornerShape(Radius.l),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) { Box(Modifier.fillMaxWidth().height(84.dp).shimmer()) }
                }
            }
        }
        // List rows
        repeat(5) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.m),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(Spacing.s12), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(CircleShape).shimmer())
                    Spacer(Modifier.width(Spacing.s12))
                    Column(Modifier.weight(1f), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.s8)) {
                        Box(Modifier.fillMaxWidth(0.6f).height(14.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                        Box(Modifier.fillMaxWidth(0.4f).height(12.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                        Box(Modifier.size(width = 60.dp, height = 24.dp).clip(RoundedCornerShape(50)).shimmer())
                    }
                }
            }
        }
    }
}

@Composable
fun IzinApprovalSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.s12)) {
        repeat(3) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.m),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(Spacing.s12), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.s8)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).clip(CircleShape).shimmer())
                        Spacer(Modifier.width(Spacing.s12))
                        Column(Modifier.weight(1f)) {
                            Box(Modifier.fillMaxWidth(0.5f).height(14.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                            Box(Modifier.fillMaxWidth(0.3f).height(12.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                    Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(8.dp)).shimmer())
                    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.s8)) {
                        Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(12.dp)).shimmer())
                        Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(12.dp)).shimmer())
                    }
                }
            }
        }
    }
}
