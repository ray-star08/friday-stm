package com.gynda.fridaystm.ui.component

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gynda.fridaystm.ui.theme.FridaySTMTheme

/**
 * A large, rounded "fitness app" call-to-action button.
 *
 * Domain-agnostic on purpose (SKILL.md §4): it knows nothing about `HomeAction`.
 * The caller maps its state — text, colors, [enabled], [loading], [icon] — so this
 * button stays reusable across screens. [loading] both shows a spinner and swallows
 * clicks, so an in-flight submit can't be double-fired.
 */
@Composable
fun BigActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    Button(
        onClick = { if (!loading) onClick() },
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.extraLarge,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            // Keep the disabled tint tied to the requested color so "done" states read
            // as an intentional muted version of the action, not a generic gray.
            disabledContainerColor = containerColor.copy(alpha = 0.38f),
            disabledContentColor = contentColor.copy(alpha = 0.62f),
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                strokeWidth = 3.dp,
                color = contentColor,
                modifier = Modifier.size(26.dp),
            )
        } else {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null, // label already conveys the action
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(text = text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Preview(showBackground = true, name = "BigActionButton · Enabled")
@Composable
private fun BigActionButtonEnabledPreview() {
    FridaySTMTheme {
        BigActionButton(
            text = "Check-in Apel",
            onClick = {},
            icon = Icons.Filled.LocationOn,
        )
    }
}

@Preview(showBackground = true, name = "BigActionButton · Loading")
@Composable
private fun BigActionButtonLoadingPreview() {
    FridaySTMTheme {
        BigActionButton(text = "Check-in Apel", onClick = {}, loading = true)
    }
}

@Preview(showBackground = true, name = "BigActionButton · Done (disabled)")
@Composable
private fun BigActionButtonDonePreview() {
    FridaySTMTheme {
        BigActionButton(
            text = "Apel Tercatat",
            onClick = {},
            enabled = false,
            icon = Icons.Filled.CheckCircle,
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
        )
    }
}
