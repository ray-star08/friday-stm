package com.gynda.fridaystm.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.gynda.fridaystm.R
import com.gynda.fridaystm.data.model.User
import com.gynda.fridaystm.ui.theme.FridaySTMTheme
import com.gynda.fridaystm.viewmodel.ProfileUiState
import com.gynda.fridaystm.viewmodel.ProfileViewModel

/**
 * Profile — stateful holder (SKILL.md §4.1). Owns [ProfileViewModel], collects
 * state lifecycle-aware, and triggers sign-out then navigates.
 *
 * @param onLogout invoked after the user signs out.
 */
@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProfileContent(
        state = state,
        onLogout = {
            viewModel.signOut()
            onLogout()
        },
        modifier = modifier,
    )
}

/** Profile — stateless content; renders purely from [state]. */
@Composable
fun ProfileContent(
    state: ProfileUiState,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        ProfileUiState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        is ProfileUiState.Error -> Box(
            modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(state.messageResId),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        is ProfileUiState.Ready -> ReadyProfile(state.user, onLogout, modifier)
    }
}

@Composable
private fun ReadyProfile(user: User, onLogout: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        if (user.photoUrl.isNotBlank()) {
            AsyncImage(
                model = user.photoUrl,
                contentDescription = stringResource(R.string.cd_profile_photo),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(96.dp).clip(CircleShape),
            )
            Spacer(Modifier.height(16.dp))
        }
        Text(
            text = user.nama.ifBlank { stringResource(R.string.home_default_name) },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(24.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                InfoRow(stringResource(R.string.profile_nis), user.nis)
                Spacer(Modifier.height(8.dp))
                InfoRow(stringResource(R.string.profile_kelas), user.kelas)
                Spacer(Modifier.height(8.dp))
                InfoRow(stringResource(R.string.profile_grade), user.grade.toString())
            }
        }

        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(stringResource(R.string.profile_logout))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value.ifBlank { "-" },
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileReadyPreview() {
    FridaySTMTheme {
        ProfileContent(
            state = ProfileUiState.Ready(
                User(uid = "u1", nis = "2024011", nama = "Budi", grade = 11, kelas = "XI RPL 1"),
            ),
            onLogout = {},
        )
    }
}
