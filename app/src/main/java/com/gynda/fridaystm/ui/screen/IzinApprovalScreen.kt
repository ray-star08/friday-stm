package com.gynda.fridaystm.ui.screen

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.gynda.fridaystm.R
import com.gynda.fridaystm.data.model.IzinRecord
import com.gynda.fridaystm.ui.component.EmptyState
import com.gynda.fridaystm.ui.component.IzinApprovalSkeleton
import com.gynda.fridaystm.ui.component.IzinStatusBadge
import com.gynda.fridaystm.ui.theme.Radius
import com.gynda.fridaystm.ui.theme.Spacing
import com.gynda.fridaystm.util.ApprovalFilter
import com.gynda.fridaystm.viewmodel.IzinApprovalViewModel

/**
 * Wali Kelas — Izin Approval — stateful holder (SKILL.md §4.1).
 *
 * Real-time stream per spec: filter status + kelas → `izinList`.
 * Actions: setujui / tolak (dengan catatan) → `updateIzinStatus`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IzinApprovalScreen(
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: IzinApprovalViewModel = viewModel(factory = IzinApprovalViewModel.factory()),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(state.actionSuccessMessage) {
        state.actionSuccessMessage?.let {
            snackbarHost.showSnackbar(it)
            viewModel.onMessagesConsumed()
        }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHost.showSnackbar(it)
            viewModel.onMessagesConsumed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.izin_approval_title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        modifier = modifier,
    ) { padding ->
        IzinApprovalContent(
            state = state,
            onFilterSelected = viewModel::onFilterSelected,
            onClassSelected = viewModel::onClassSelected,
            onApprove = viewModel::approveIzin,
            onReject = viewModel::rejectIzin,
            modifier = Modifier.padding(padding),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IzinApprovalContent(
    state: com.gynda.fridaystm.viewmodel.IzinApprovalUiState,
    onFilterSelected: (ApprovalFilter) -> Unit,
    onClassSelected: (String) -> Unit,
    onApprove: (String) -> Unit,
    onReject: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var rejectTarget by remember { mutableStateOf<IzinRecord?>(null) }
    var previewImageUrl by remember { mutableStateOf<String?>(null) }

    if (rejectTarget != null) {
        RejectReasonDialog(
            izin = rejectTarget!!,
            onDismiss = { rejectTarget = null },
            onConfirm = { note ->
                onReject(rejectTarget!!.id, note)
                rejectTarget = null
            },
        )
    }

    if (previewImageUrl != null) {
        Dialog(
            onDismissRequest = { previewImageUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.8f))
                    .clickable { previewImageUrl = null },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = previewImageUrl,
                    contentDescription = "Bukti izin",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .clip(RoundedCornerShape(Radius.m))
                        .background(MaterialTheme.colorScheme.surface),
                )
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Class filter
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.s16, vertical = Spacing.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.weight(1f),
            ) {
                OutlinedTextField(
                    value = state.selectedClass,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.teacher_filter_kelas)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    val options = listOf("ALL") + state.availableClasses
                    options.forEach { kelas ->
                        DropdownMenuItem(
                            text = { Text(if (kelas == "ALL") "Semua Kelas" else kelas) },
                            onClick = {
                                onClassSelected(kelas)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }

        // Tabs — PrimaryTabRow M3
        val tabs = listOf(
            ApprovalFilter.PENDING to "Menunggu",
            ApprovalFilter.APPROVED to "Disetujui",
            ApprovalFilter.REJECTED to "Ditolak",
        )
        val selectedIndex = tabs.indexOfFirst { it.first == state.selectedFilter }.let { if (it == -1) 0 else it }
        PrimaryTabRow(selectedTabIndex = selectedIndex) {
            tabs.forEachIndexed { index, (filter, label) ->
                Tab(
                    selected = selectedIndex == index,
                    onClick = { onFilterSelected(filter) },
                    text = { Text(label, style = MaterialTheme.typography.labelLarge) },
                )
            }
        }

        Crossfade(targetState = state.isLoading, label = "izinLoading") { loading ->
            when {
                loading -> Box(Modifier.fillMaxSize().padding(Spacing.s16)) { IzinApprovalSkeleton() }
                state.izinList.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Default.Info,
                        title = when (state.selectedFilter) {
                            ApprovalFilter.PENDING -> "Tidak ada pengajuan menunggu"
                            ApprovalFilter.APPROVED -> "Belum ada yang disetujui"
                            ApprovalFilter.REJECTED -> "Belum ada yang ditolak"
                            else -> "Tidak ada data"
                        },
                        body = "Pengajuan izin/sakit akan muncul di sini",
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(Spacing.s12),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s12),
                    ) {
                        items(state.izinList, key = { it.id }) { izin ->
                            IzinApprovalCard(
                                izin = izin,
                                showActions = state.selectedFilter == ApprovalFilter.PENDING,
                                onApprove = { onApprove(izin.id) },
                                onReject = { rejectTarget = izin },
                                onImageClick = { previewImageUrl = izin.proofUrl },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IzinApprovalCard(
    izin: IzinRecord,
    showActions: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onImageClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.m),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(Spacing.s12), verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val initial = izin.nama.trim().firstOrNull()?.uppercase() ?: "?"
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(initial, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(Spacing.s12))
                Column(Modifier.weight(1f)) {
                    Text(izin.nama.ifBlank { "-" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text("Kelas: ${izin.kelas.ifBlank { "-" }} • ${izin.tipe}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    Text("${izin.startDate} s/d ${izin.endDate}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (izin.status != com.gynda.fridaystm.util.IzinStatus.PENDING) {
                    IzinStatusBadge(statusWire = izin.status)
                }
            }

            Text("Alasan:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text(izin.alasan, style = MaterialTheme.typography.bodyMedium)

            if (izin.proofUrl.isNotBlank()) {
                Text("Bukti Surat:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                AsyncImage(
                    model = izin.proofUrl,
                    contentDescription = "Bukti izin ${izin.nama}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(Radius.s))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onImageClick),
                )
                Text("Tap gambar untuk preview", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (izin.approvalNote.isNotBlank()) {
                Text("Catatan Wali Kelas: ${izin.approvalNote}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (showActions) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                    Button(
                        onClick = onApprove,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f),
                    ) { Text("Setujui") }
                    Button(
                        onClick = onReject,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f),
                    ) { Text("Tolak") }
                }
            }
        }
    }
}

@Composable
private fun RejectReasonDialog(
    izin: IzinRecord,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var note by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tolak Pengajuan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                Text("Berikan catatan alasan penolakan untuk ${izin.nama} (${izin.kelas}):", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Catatan") },
                    placeholder = { Text("Contoh: Bukti tidak jelas / tanggal tidak sesuai") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(note) },
                enabled = note.trim().isNotBlank(),
            ) { Text("Kirim Penolakan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        },
    )
}
