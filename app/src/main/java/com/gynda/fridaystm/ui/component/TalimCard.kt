package com.gynda.fridaystm.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gynda.fridaystm.R
import com.gynda.fridaystm.data.model.TalimSummary
import com.gynda.fridaystm.ui.theme.FridaySTMTheme
import com.gynda.fridaystm.viewmodel.SubmitStatus
import com.gynda.fridaystm.viewmodel.TalimUiState

/**
 * The Ta'lim class-summary section on Home.
 *
 * Role-driven (SKILL.md §3.3 — decided in the ViewModel, passed via [state]): a
 * class rep/admin ([TalimUiState.canSubmit]) sees the submit form (or a read-back
 * once submitted); a plain student sees nothing here (their Ta'lim is the selfie
 * check-in). Renders nothing when the user can neither submit nor has a summary.
 */
@Composable
fun TalimCard(
    state: TalimUiState,
    submitStatus: SubmitStatus,
    onSubmit: (penceramah: String, tema: String, ringkasan: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val summary = state.summary
    // Nothing to render for a plain student, or anyone before a summary exists.
    if (!state.canSubmit && summary == null) return

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.talim_summary_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            when {
                summary != null -> TalimReadBack(summary)
                state.canSubmit -> TalimSubmitForm(submitStatus, onSubmit)
            }
        }
    }
}

@Composable
private fun TalimReadBack(summary: TalimSummary) {
    Text(summary.tema, style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(4.dp))
    Text(
        text = summary.penceramah,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    Text(summary.ringkasan, style = MaterialTheme.typography.bodyMedium)
    if (summary.submittedByName.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.talim_done_by, summary.submittedByName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TalimSubmitForm(
    submitStatus: SubmitStatus,
    onSubmit: (String, String, String) -> Unit,
) {
    var penceramah by rememberSaveable { mutableStateOf("") }
    var tema by rememberSaveable { mutableStateOf("") }
    var ringkasan by rememberSaveable { mutableStateOf("") }
    val submitting = submitStatus is SubmitStatus.Submitting

    Text(
        text = stringResource(R.string.talim_prompt),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = penceramah,
        onValueChange = { penceramah = it },
        label = { Text(stringResource(R.string.talim_penceramah_label)) },
        singleLine = true,
        enabled = !submitting,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = tema,
        onValueChange = { tema = it },
        label = { Text(stringResource(R.string.talim_tema_label)) },
        singleLine = true,
        enabled = !submitting,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = ringkasan,
        onValueChange = { ringkasan = it },
        label = { Text(stringResource(R.string.talim_ringkasan_label)) },
        enabled = !submitting,
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = { onSubmit(penceramah, tema, ringkasan) },
        enabled = !submitting,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.talim_submit))
    }
    when (submitStatus) {
        is SubmitStatus.Error -> TalimStatusLine(submitStatus.messageResId, isError = true)
        SubmitStatus.Success -> TalimStatusLine(R.string.talim_submitted, isError = false)
        else -> Unit
    }
}

@Composable
private fun TalimStatusLine(resId: Int, isError: Boolean) {
    Spacer(Modifier.height(6.dp))
    Text(
        text = stringResource(resId),
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    )
}

@Preview(showBackground = true, name = "Ta'lim · Class-rep form")
@Composable
private fun TalimCardFormPreview() {
    FridaySTMTheme {
        TalimCard(
            state = TalimUiState(date = "2026-08-28", kelas = "XI RPL 1", canSubmit = true),
            submitStatus = SubmitStatus.Idle,
            onSubmit = { _, _, _ -> },
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true, name = "Ta'lim · Submitted")
@Composable
private fun TalimCardReadBackPreview() {
    FridaySTMTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TalimCard(
                state = TalimUiState(
                    date = "2026-08-28",
                    kelas = "XI RPL 1",
                    summary = TalimSummary(
                        tema = "Adab Menuntut Ilmu",
                        penceramah = "Ust. Fulan",
                        ringkasan = "Pentingnya niat dan adab kepada guru.",
                        submittedByName = "Ketua Kelas",
                    ),
                    canSubmit = true,
                ),
                submitStatus = SubmitStatus.Idle,
                onSubmit = { _, _, _ -> },
            )
        }
    }
}
