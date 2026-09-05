package com.gynda.fridaystm.ui.component

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Reusable runtime-permission prompt (SKILL.md §8): a rationale line plus a single
 * action button. Stateless and previewable — the caller decides, from its own
 * permission state, whether to show the "request" or the "open Settings" variant
 * by passing the appropriate [messageResId] / [actionLabelResId] / [onAction].
 *
 * Shared by the Camera (task 4.3) and Home geofence (task 4.1) check-in gates so
 * the wording and layout stay identical across both.
 */
@Composable
fun PermissionRequestContent(
    @StringRes messageResId: Int,
    @StringRes actionLabelResId: Int,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(messageResId),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAction, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(stringResource(actionLabelResId))
        }
    }
}

/**
 * Opens this app's system settings page so the user can flip a permission that was
 * denied permanently ("don't ask again"). Used by permission gates once the runtime
 * request can no longer show a dialog.
 */
fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}
