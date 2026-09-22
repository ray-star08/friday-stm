package com.gynda.fridaystm.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.gynda.fridaystm.R
import com.gynda.fridaystm.data.model.PresensiRecord
import com.gynda.fridaystm.data.model.StudentAttendanceItem
import com.gynda.fridaystm.data.model.StudentPresenceStatus
import com.gynda.fridaystm.data.model.TeacherStats
import com.gynda.fridaystm.data.model.User
import com.gynda.fridaystm.ui.component.EmptyState
import com.gynda.fridaystm.ui.component.ErrorState
import com.gynda.fridaystm.ui.component.StudentStatusBadge
import com.gynda.fridaystm.ui.component.TeacherDashboardSkeleton
import com.gynda.fridaystm.ui.theme.Elevation
import com.gynda.fridaystm.ui.theme.FridaySTMTheme
import com.gynda.fridaystm.ui.theme.Radius
import com.gynda.fridaystm.ui.theme.Spacing
import com.gynda.fridaystm.viewmodel.TeacherDashboardViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Teacher/Admin Dashboard — stateful holder (SKILL.md §4.1).
 *
 * Real-time monitoring per spec: filter kelas/tanggal → Firestore streams
 * via [TeacherDashboardViewModel], 4 summary cards, student list with badge,
 * and detail bottom sheet (GPS + foto bukti).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherDashboardScreen(
    onLogout: (() -> Unit)? = null,
    onOpenIzinApproval: (() -> Unit)? = null,
    onOpenExportReport: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: TeacherDashboardViewModel = viewModel(factory = TeacherDashboardViewModel.factory()),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    TeacherDashboardContent(
        state = uiState,
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        onClassSelected = viewModel::onClassSelected,
        onDateSelected = viewModel::onDateSelected,
        onOpenIzinApproval = onOpenIzinApproval,
        onOpenExportReport = onOpenExportReport,
        onLogout = {
            viewModel.logout()
            onLogout?.invoke()
        },
        modifier = modifier,
    )
}

/**
 * Stateless content — pure rendering from state, so previewable (SKILL.md §4.1).
 *
 * Split out so `Home`-style combine logic stays in the ViewModel and this
 * composable is branch-testable with fake state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherDashboardContent(
    state: com.gynda.fridaystm.viewmodel.TeacherDashboardUiState,
    onClassSelected: (String) -> Unit,
    onDateSelected: (String) -> Unit,
    onOpenIzinApproval: (() -> Unit)? = null,
    onOpenExportReport: (() -> Unit)? = null,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onLogout: (() -> Unit)? = null,
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
                        onLogout?.invoke()
                    },
                ) { Text(stringResource(R.string.profile_logout_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(R.string.profile_logout_cancel))
                }
            },
        )
    }
    var selectedStudent by remember { mutableStateOf<StudentAttendanceItem?>(null) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    if (showDatePicker) {
        val initialMillis = try {
            LocalDate.parse(state.selectedDate).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (_: Exception) { null }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        val picked = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                            .toString() // yyyy-MM-dd
                        onDateSelected(picked)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Batal") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (selectedStudent != null) {
        StudentDetailBottomSheet(
            item = selectedStudent!!,
            onDismiss = { selectedStudent = null },
        )
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Spacing.s20),
            verticalArrangement = Arrangement.spacedBy(Spacing.s16),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.redesign_brand), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(Spacing.s8))
                        Text(stringResource(R.string.redesign_teacher_heading), style = MaterialTheme.typography.headlineMedium)
                        Text(stringResource(R.string.teacher_dashboard_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (onLogout != null) {
                        IconButton(onClick = { showLogoutDialog = true }) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, stringResource(R.string.profile_logout), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                TeacherFilterBar(
                    selectedClass = state.selectedClass,
                    selectedDate = state.selectedDate,
                    availableClasses = state.availableClasses,
                    onClassSelected = onClassSelected,
                    onDateClick = { showDatePicker = true },
                    onDateSelected = onDateSelected,
                )
            }
            if (state.isLoading) {
                item { TeacherDashboardSkeleton() }
            } else {
                item { TeacherSummaryGrid(stats = state.stats) }
            }
            if (onOpenIzinApproval != null) {
                item {
                    com.gynda.fridaystm.ui.component.ActionMenuRow(
                        title = stringResource(R.string.redesign_approval),
                        subtitle = stringResource(R.string.redesign_approval_body),
                        icon = Icons.Default.Person,
                        onClick = onOpenIzinApproval,
                    )
                }
            }
            if (onOpenExportReport != null) {
                item {
                    com.gynda.fridaystm.ui.component.ActionMenuRow(
                        title = stringResource(R.string.redesign_export),
                        subtitle = stringResource(R.string.redesign_export_body),
                        icon = Icons.Default.DateRange,
                        onClick = onOpenExportReport,
                    )
                }
            }
            if (state.error != null) {
                item { ErrorState(message = state.error, onRetry = onRefresh) }
            }
            if (!state.isLoading) {
                item {
                    Text(stringResource(R.string.teacher_dashboard_list_title, state.students.size), style = MaterialTheme.typography.titleMedium)
                }
                if (state.students.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Default.Search,
                            title = stringResource(R.string.teacher_dashboard_empty),
                            body = stringResource(R.string.redesign_teacher_empty_body),
                        )
                    }
                } else {
                    items(state.students, key = { it.user.uid }) { item ->
                        StudentAttendanceCard(item, onClick = { selectedStudent = item }, modifier = Modifier.animateItem())
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeacherFilterBar(
    selectedClass: String,
    selectedDate: String,
    availableClasses: List<String>,
    onClassSelected: (String) -> Unit,
    onDateClick: () -> Unit,
    onDateSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Class dropdown (ExposedDropdown)
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.weight(1f),
        ) {
            OutlinedTextField(
                value = selectedClass,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.teacher_filter_kelas)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                availableClasses.forEach { kelas ->
                    DropdownMenuItem(
                        text = { Text(kelas) },
                        onClick = {
                            onClassSelected(kelas)
                            expanded = false
                        },
                    )
                }
            }
        }

        // Date picker chip / button
        AssistChip(
            onClick = onDateClick,
            label = { Text(formatDisplayDate(selectedDate), style = MaterialTheme.typography.labelLarge) },
            leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, Modifier.size(18.dp)) },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        )
    }
    // Hidden date picker integration: for now, clicking chip sets today if empty.
    // Real app would show DatePickerDialog; keep minimal for testability.
}

@Composable
private fun TeacherSummaryGrid(
    stats: TeacherStats,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s8)) {
            StatCard(
                label = stringResource(R.string.teacher_stat_hadir),
                value = stats.totalHadir.toString(),
                unit = "siswa",
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = stringResource(R.string.teacher_stat_izin),
                value = stats.totalIzin.toString(),
                unit = "siswa",
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s8)) {
            StatCard(
                label = stringResource(R.string.teacher_stat_belum),
                value = stats.totalBelum.toString(),
                unit = "siswa",
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = stringResource(R.string.teacher_stat_larkam),
                value = String.format(Locale.forLanguageTag("id-ID"), "%.1f", stats.totalLarkamKm),
                unit = "KM",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    unit: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(Radius.l),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(com.gynda.fridaystm.ui.theme.ComponentSize.border, containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.card),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(Spacing.s12),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.s4))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(text = value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(Spacing.s4))
                Text(text = unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StudentAttendanceCard(
    item: StudentAttendanceItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(stiffness = 400f),
        label = "pressScale",
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                onClick = onClick,
            )
            .then(Modifier.let { m -> m }),
        shape = RoundedCornerShape(Radius.m),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.card),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(Spacing.s12)
                .graphicsLayer(scaleX = scale, scaleY = scale),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar
            if (item.user.photoUrl.isNotBlank()) {
                AsyncImage(
                    model = item.user.photoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                )
            } else {
                val initial = item.user.nama.trim().firstOrNull()?.uppercase() ?: "?"
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = initial, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Spacer(Modifier.width(Spacing.s12))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.user.nama.ifBlank { "-" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    text = "NISN: ${item.user.nis.ifBlank { "-" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(Spacing.s4))
                val time = item.presensi?.timestamp?.let { extractTime(it) }
                StudentStatusBadge(status = item.status, time = time)
            }
            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudentDetailBottomSheet(
    item: StudentAttendanceItem,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val initial = item.user.nama.trim().firstOrNull()?.uppercase() ?: "?"
                Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Text(initial, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(item.user.nama, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${item.user.nis} • ${item.user.kelas}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Status badge large
            val (badgeText, badgeColor, badgeContainer) = when (item.status) {
                StudentPresenceStatus.HADIR -> Triple("Hadir", MaterialTheme.colorScheme.onPrimary, MaterialTheme.colorScheme.primary)
                StudentPresenceStatus.IZIN -> Triple("Izin / Sakit", MaterialTheme.colorScheme.onTertiaryContainer, MaterialTheme.colorScheme.tertiaryContainer)
                StudentPresenceStatus.BELUM -> Triple("Belum Absen", MaterialTheme.colorScheme.onErrorContainer, MaterialTheme.colorScheme.errorContainer)
            }
            Surface(color = badgeContainer, shape = RoundedCornerShape(50)) {
                Text(badgeText, style = MaterialTheme.typography.labelLarge, color = badgeColor, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }

            // Foto bukti (presensi)
            if (item.presensi?.imageUrl?.isNotBlank() == true) {
                Text(stringResource(R.string.teacher_detail_foto), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                AsyncImage(
                    model = item.presensi.imageUrl,
                    contentDescription = "Bukti presensi ${item.user.nama}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
            // Izin proof if any
            if (item.izin?.proofUrl?.isNotBlank() == true) {
                Text("Bukti Izin", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                AsyncImage(
                    model = item.izin.proofUrl,
                    contentDescription = "Bukti izin ${item.user.nama}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Text(item.izin.alasan, style = MaterialTheme.typography.bodyMedium)
                Text("${item.izin.startDate} s/d ${item.izin.endDate}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // GPS location
            val lat = item.presensi?.lat
            val lng = item.presensi?.lng
            if (lat != null && lng != null) {
                Text(stringResource(R.string.teacher_detail_lokasi), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(String.format(Locale.US, "%.6f, %.6f", lat, lng), style = MaterialTheme.typography.bodyMedium)
                // Mini-map could be embedded; keep text for simplicity
            } else if (item.status == StudentPresenceStatus.HADIR) {
                Text("Lokasi tidak tersedia", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Larkam distance
            if (item.larkamDistanceKm != null && item.larkamDistanceKm > 0) {
                Text("Larkam: ${String.format(Locale.forLanguageTag("id-ID"), "%.1f KM", item.larkamDistanceKm)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun extractTime(iso: String): String = try {
    // iso like 2026-09-06T08:20:57 → 08:20
    iso.substringAfter("T").take(5)
} catch (_: Exception) { "--:--" }

private fun formatDisplayDate(isoDate: String): String = try {
    LocalDate.parse(isoDate).format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.forLanguageTag("id-ID")))
} catch (_: Exception) { isoDate.ifBlank { "Pilih Tanggal" } }

// Previews
@Preview(showBackground = true, name = "Teacher Dashboard · Sample")
@Composable
private fun TeacherDashboardPreview() {
    FridaySTMTheme {
        TeacherDashboardContent(
            state = com.gynda.fridaystm.viewmodel.TeacherDashboardUiState(
                selectedClass = "XI RPL A",
                selectedDate = "2026-09-06",
                isLoading = false,
                stats = TeacherStats(totalHadir = 12, totalIzin = 3, totalBelum = 5, totalLarkamKm = 42.5),
                students = listOf(
                    StudentAttendanceItem(
                        user = User(uid="1", nis="001", nama="Andi", kelas="XI RPL A"),
                        status = StudentPresenceStatus.HADIR,
                        presensi = PresensiRecord(userId="1", timestamp="2026-09-06T06:45:00", imageUrl="", lat=-6.88, lng=107.53),
                    ),
                    StudentAttendanceItem(
                        user = User(uid="2", nis="002", nama="Budi", kelas="XI RPL A"),
                        status = StudentPresenceStatus.IZIN,
                    ),
                    StudentAttendanceItem(
                        user = User(uid="3", nis="003", nama="Citra", kelas="XI RPL A"),
                        status = StudentPresenceStatus.BELUM,
                    ),
                ),
            ),
            onClassSelected = {},
            onDateSelected = {},
        )
    }
}
