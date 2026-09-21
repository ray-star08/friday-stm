package com.gynda.fridaystm.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.gynda.fridaystm.data.model.ProfileStats
import com.gynda.fridaystm.data.model.User
import com.gynda.fridaystm.ui.theme.FridaySTMTheme
import com.gynda.fridaystm.viewmodel.ProfileViewModel
import com.gynda.fridaystm.viewmodel.UserProfileUiState
import java.util.Locale

/**
 * Profile — stateful holder (SKILL.md §4.1). Owns [ProfileViewModel], collects
 * state lifecycle-aware, and forwards the one-shot logout event to navigation.
 *
 * @param onLogout invoked exactly once after [ProfileViewModel.logout].
 */
@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val logoutEvent by viewModel.logoutEvent.collectAsStateWithLifecycle()

    // Event sekali-tampil → navigasi Login (backstack dikosongkan di NavHost).
    LaunchedEffect(logoutEvent) {
        if (logoutEvent) {
            viewModel.onLogoutConsumed()
            onLogout()
        }
    }

    ProfileContent(
        state = state,
        onLogoutConfirmed = viewModel::logout,
        modifier = modifier,
    )
}

/** Profile — stateless content; renders purely from [state]. */
@Composable
fun ProfileContent(
    state: UserProfileUiState,
    onLogoutConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showLogoutDialog by rememberSaveable { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(stringResource(R.string.profile_logout_title)) },
            text = { Text(stringResource(R.string.profile_logout_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogoutConfirmed()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.profile_logout_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(R.string.profile_logout_cancel))
                }
            },
        )
    }

    when (state) {
        UserProfileUiState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        is UserProfileUiState.Error -> Box(
            modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }

        is UserProfileUiState.Success -> ProfileReady(
            user = state.user,
            email = state.email,
            stats = state.stats,
            onLogoutClick = { showLogoutDialog = true },
            modifier = modifier,
        )
    }
}

@Composable
private fun ProfileReady(
    user: User,
    email: String,
    stats: ProfileStats,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // --- Header card: foto, nama, NISN, kelas ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ProfilePhoto(photoUrl = user.photoUrl, nama = user.nama)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = user.nama.ifBlank { stringResource(R.string.home_default_name) },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${stringResource(R.string.profile_nisn)}: ${user.nis.ifBlank { "-" }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = user.kelas.ifBlank { "-" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // --- Ringkasan statistik: Hadir · Larkam · Izin ---
        Text(
            text = stringResource(R.string.profile_stats_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard(
                value = stats.presensiCount.toString(),
                label = stringResource(R.string.profile_stats_hadir),
                unit = stringResource(R.string.profile_stats_hadir_unit),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                value = formatKm(stats.larkamDistanceKm),
                label = stringResource(R.string.profile_stats_larkam),
                unit = stringResource(R.string.profile_stats_larkam_unit),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                value = stats.izinCount.toString(),
                label = stringResource(R.string.profile_stats_izin),
                unit = stringResource(R.string.profile_stats_izin_unit),
                modifier = Modifier.weight(1f),
            )
        }

        // --- Informasi akun ---
        Text(
            text = stringResource(R.string.profile_account_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                InfoRow(stringResource(R.string.profile_email), email)
                Spacer(Modifier.height(8.dp))
                InfoRow(stringResource(R.string.profile_status), stringResource(R.string.profile_status_active))
                Spacer(Modifier.height(8.dp))
                InfoRow(stringResource(R.string.profile_school), stringResource(R.string.profile_school_name))
            }
        }

        // --- Danger zone: keluar akun ---
        Button(
            onClick = onLogoutClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(stringResource(R.string.profile_logout))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ProfilePhoto(photoUrl: String, nama: String, modifier: Modifier = Modifier) {
    if (photoUrl.isNotBlank()) {
        AsyncImage(
            model = photoUrl,
            contentDescription = stringResource(R.string.cd_profile_photo),
            contentScale = ContentScale.Crop,
            modifier = modifier.size(96.dp).clip(CircleShape),
        )
    } else {
        // Fallback inisial saat belum ada foto (tanpa placeholder asset).
        val initial = nama.trim().firstOrNull()?.uppercase() ?: "?"
        Box(
            modifier = modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initial,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, unit: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

/** KM dengan 1 desimal memakai koma Indonesia (mis. `12,4`). */
private fun formatKm(km: Double): String =
    String.format(Locale.forLanguageTag("id-ID"), "%.1f", km)

@Preview(showBackground = true, name = "Profile · Success")
@Composable
private fun ProfileSuccessPreview() {
    FridaySTMTheme {
        ProfileContent(
            state = UserProfileUiState.Success(
                user = User(
                    uid = "u1",
                    nis = "2024011",
                    nama = "Budi",
                    grade = 11,
                    kelas = "XI RPL 1",
                    photoUrl = "",
                ),
                email = "budi@smkn1cimahi.sch.id",
                stats = ProfileStats(presensiCount = 12, larkamDistanceKm = 8.4, izinCount = 2),
            ),
            onLogoutConfirmed = {},
        )
    }
}

@Preview(showBackground = true, name = "Profile · Loading")
@Composable
private fun ProfileLoadingPreview() {
    FridaySTMTheme {
        ProfileContent(
            state = UserProfileUiState.Loading,
            onLogoutConfirmed = {},
        )
    }
}
