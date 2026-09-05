package com.gynda.fridaystm.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gynda.fridaystm.R
import com.gynda.fridaystm.data.model.SenamSession
import com.gynda.fridaystm.ui.theme.FridaySTMTheme
import com.gynda.fridaystm.viewmodel.SenamUiState
import com.gynda.fridaystm.viewmodel.SubmitStatus

/**
 * The weekly Senam section on Home.
 *
 * Role-driven (SKILL.md §3.3 — the decision is made in the ViewModel and passed in
 * via [state]): an instructor/admin ([SenamUiState.canManage]) sees a form to set
 * the week's YouTube video; everyone else ([SenamUiState.canView]) watches the
 * embedded player once a video exists. Renders nothing for a plain instructor with
 * no video set yet and for roles that can neither manage nor view.
 *
 * Stateless apart from the local text field; raises the pasted URL through
 * [onSetVideo] so it stays previewable.
 */
@Composable
fun SenamCard(
    state: SenamUiState,
    setStatus: SubmitStatus,
    onSetVideo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = state.session
    // Nothing to render: a viewer with no video yet, or a role with no Senam access.
    if (!state.canManage && (!state.canView || session == null)) return

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.senam_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            if (session != null && state.canView) {
                YouTubePlayer(
                    videoId = session.videoId,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)),
                )
                Spacer(Modifier.height(8.dp))
            }

            if (state.canManage) {
                SenamManageForm(
                    hasExisting = session != null,
                    setStatus = setStatus,
                    onSetVideo = onSetVideo,
                )
            } else if (session == null) {
                // Viewer, video not set yet (only reachable when canManage stays false).
                Text(
                    text = stringResource(R.string.senam_not_set),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SenamManageForm(
    hasExisting: Boolean,
    setStatus: SubmitStatus,
    onSetVideo: (String) -> Unit,
) {
    var url by rememberSaveable { mutableStateOf("") }
    val submitting = setStatus is SubmitStatus.Submitting

    Text(
        text = stringResource(
            if (hasExisting) R.string.senam_manage_replace else R.string.senam_manage_set,
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = url,
        onValueChange = { url = it },
        label = { Text(stringResource(R.string.senam_url_label)) },
        singleLine = true,
        enabled = !submitting,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = { onSetVideo(url) },
        enabled = url.isNotBlank() && !submitting,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.senam_save))
    }
    when (setStatus) {
        is SubmitStatus.Error -> StatusLine(setStatus.messageResId, isError = true)
        SubmitStatus.Success -> StatusLine(R.string.senam_saved, isError = false)
        else -> Unit
    }
}

@Composable
private fun StatusLine(resId: Int, isError: Boolean) {
    Spacer(Modifier.height(6.dp))
    Text(
        text = stringResource(resId),
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    )
}

@Preview(showBackground = true, name = "Senam · Viewer")
@Composable
private fun SenamCardViewerPreview() {
    FridaySTMTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SenamCard(
                state = SenamUiState(
                    weekId = "2026-W35",
                    session = SenamSession(weekId = "2026-W35", videoId = "dQw4w9WgXcQ"),
                    canManage = false,
                    canView = true,
                ),
                setStatus = SubmitStatus.Idle,
                onSetVideo = {},
            )
        }
    }
}

@Preview(showBackground = true, name = "Senam · Instructor form")
@Composable
private fun SenamCardManagePreview() {
    FridaySTMTheme {
        SenamCard(
            state = SenamUiState(weekId = "2026-W35", session = null, canManage = true, canView = false),
            setStatus = SubmitStatus.Idle,
            onSetVideo = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
