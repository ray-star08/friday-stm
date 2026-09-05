package com.gynda.fridaystm.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.gynda.fridaystm.R
import com.gynda.fridaystm.ui.theme.FridaySTMTheme
import androidx.compose.ui.res.stringResource

/**
 * Shows a captured/uploaded selfie with a status badge and the check-in time
 * (Milestone 5.1). Stateless: it takes the image URL and plain display values —
 * no ViewModel, no I/O (SKILL.md §4.2). Coil's [AsyncImage] streams the photo.
 *
 * @param selfieUrl Cloudinary `secure_url` of the selfie.
 * @param statusLabel e.g. "Presensi Masuk" / "Presensi Pulang".
 * @param time device `HH:mm` shown under the badge.
 * @param valid whether the check-in was inside the geofence (colors the badge).
 */
@Composable
fun SelfiePreviewCard(
    selfieUrl: String,
    statusLabel: String,
    time: String,
    modifier: Modifier = Modifier,
    valid: Boolean = true,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            AsyncImage(
                model = selfieUrl,
                contentDescription = stringResource(R.string.cd_selfie_card, statusLabel),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusBadge(label = statusLabel, valid = valid)
                Text(
                    text = stringResource(R.string.history_phase_time, time),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(label: String, valid: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val container = if (valid) scheme.secondaryContainer else scheme.errorContainer
    val content = if (valid) scheme.onSecondaryContainer else scheme.onErrorContainer
    Surface(color = container, shape = RoundedCornerShape(50), modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = content,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Preview(showBackground = true, name = "SelfiePreviewCard · Valid")
@Composable
private fun SelfiePreviewCardValidPreview() {
    FridaySTMTheme {
        SelfiePreviewCard(
            selfieUrl = "",
            statusLabel = "Presensi Masuk",
            time = "06:12",
            valid = true,
        )
    }
}

@Preview(showBackground = true, name = "SelfiePreviewCard · Out of area")
@Composable
private fun SelfiePreviewCardInvalidPreview() {
    FridaySTMTheme {
        SelfiePreviewCard(
            selfieUrl = "",
            statusLabel = "Presensi Pulang",
            time = "08:05",
            valid = false,
        )
    }
}
