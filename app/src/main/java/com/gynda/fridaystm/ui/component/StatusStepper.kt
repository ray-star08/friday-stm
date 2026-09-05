package com.gynda.fridaystm.ui.component

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gynda.fridaystm.R
import com.gynda.fridaystm.data.model.AttendanceRecord
import com.gynda.fridaystm.data.model.CheckoutStamp
import com.gynda.fridaystm.data.model.PembiasaanStamp
import com.gynda.fridaystm.domain.Activity
import com.gynda.fridaystm.domain.FridayPhase
import com.gynda.fridaystm.ui.theme.FridaySTMTheme

/**
 * A Strava-style horizontal progress indicator for the two Friday phases:
 * **Pembiasaan → Check-out**. Completed steps fill with the success color
 * and a check; the current step is highlighted; future steps stay muted. The
 * connectors between nodes fill in as the student progresses.
 *
 * Stateless & reusable (SKILL.md §4): it takes plain domain values, derives the
 * per-step state via the pure [attendanceSteps], and applies [modifier] to its
 * root — so it is fully previewable without a ViewModel. During Pembiasaan the
 * middle step shows the rotated [activeActivity] (Ta'lim / Larkam / Senam).
 */
@Composable
fun StatusStepper(
    phase: FridayPhase,
    activeActivity: Activity?,
    record: AttendanceRecord?,
    modifier: Modifier = Modifier,
) {
    val steps = attendanceSteps(phase, activeActivity, record)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        steps.forEachIndexed { index, step ->
            StepCell(
                step = step,
                // A connector is "done" when the step it flows *out of* is done,
                // so the filled line always trails the completed nodes.
                leftConnector = if (index == 0) null else steps[index - 1].state == StepState.DONE,
                rightConnector = if (index == steps.lastIndex) null else step.state == StepState.DONE,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** One step's visual state, derived purely from the phase + today's record. */
enum class StepState { DONE, ACTIVE, UPCOMING }

/** A single node in the stepper: its label resource and current [StepState]. */
data class AttendanceStep(
    @StringRes val labelRes: Int,
    val state: StepState,
)

/**
 * Pure mapping of (phase, activity, record) → the two ordered [AttendanceStep]s.
 *
 * Kept out of the composable (SKILL.md §3.3) and free of I/O so it is trivially
 * unit-testable. A step is [StepState.DONE] once its check-in is recorded,
 * [StepState.ACTIVE] while its phase is current, else [StepState.UPCOMING].
 * `internal` so component/unit tests can drive it directly.
 */
internal fun attendanceSteps(
    phase: FridayPhase,
    activeActivity: Activity?,
    record: AttendanceRecord?,
): List<AttendanceStep> {
    fun state(done: Boolean, current: Boolean): StepState = when {
        done -> StepState.DONE
        current -> StepState.ACTIVE
        else -> StepState.UPCOMING
    }

    // The first step names the specific rotated activity when it is known.
    val pembiasaanLabel = activeActivity?.let(::activityLabelRes) ?: R.string.step_pembiasaan

    return listOf(
        AttendanceStep(
            labelRes = pembiasaanLabel,
            state = state(record?.pembiasaan?.checkedIn == true, phase == FridayPhase.PEMBIASAAN),
        ),
        AttendanceStep(
            labelRes = R.string.step_checkout,
            state = state(record?.checkout?.checkedOut == true, phase == FridayPhase.CHECKOUT),
        ),
    )
}

/**
 * One column of the stepper: the connectors + node on top, its label below.
 * Equal [Modifier.weight] across cells keeps every node — and its centered
 * label — evenly spaced. A `null` connector renders an invisible spacer so edge
 * nodes stay centered within their cell.
 */
@Composable
private fun StepCell(
    step: AttendanceStep,
    leftConnector: Boolean?,
    rightConnector: Boolean?,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(step.labelRes)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Connector(done = leftConnector, modifier = Modifier.weight(1f))
            StepNode(state = step.state, label = label)
            Connector(done = rightConnector, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = labelColor(step.state),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** The circular node; its fill/ring/icon reflect [state]. Carries the a11y label. */
@Composable
private fun StepNode(
    state: StepState,
    label: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val description = when (state) {
        StepState.DONE -> stringResource(R.string.cd_step_done, label)
        StepState.ACTIVE -> stringResource(R.string.cd_step_active, label)
        StepState.UPCOMING -> stringResource(R.string.cd_step_upcoming, label)
    }
    val base = modifier
        .size(NODE_SIZE)
        .clip(CircleShape)
        .semantics { contentDescription = description }

    when (state) {
        StepState.DONE -> Box(
            modifier = base.background(scheme.secondary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null, // node already carries the description
                tint = scheme.onSecondary,
                modifier = Modifier.size(20.dp),
            )
        }

        StepState.ACTIVE -> Box(
            modifier = base
                .background(scheme.primary.copy(alpha = 0.14f))
                .border(2.dp, scheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(scheme.primary),
            )
        }

        StepState.UPCOMING -> Box(
            modifier = base.border(2.dp, scheme.outline.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(scheme.outline.copy(alpha = 0.5f)),
            )
        }
    }
}

/** Rounded track segment between two nodes. `null` ⇒ an edge, so reserve the space only. */
@Composable
private fun Connector(done: Boolean?, modifier: Modifier = Modifier) {
    if (done == null) {
        Spacer(modifier) // keep the edge node centered within its cell
        return
    }
    val color = if (done) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    }
    Box(
        modifier = modifier
            .padding(horizontal = 4.dp)
            .height(3.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun labelColor(state: StepState): Color = when (state) {
    StepState.DONE -> MaterialTheme.colorScheme.secondary
    StepState.ACTIVE -> MaterialTheme.colorScheme.primary
    StepState.UPCOMING -> MaterialTheme.colorScheme.onSurfaceVariant
}

private val NODE_SIZE = 36.dp

// --- Previews ------------------------------------------------------------

private val previewFresh = AttendanceRecord()
private val previewThroughPembiasaan = AttendanceRecord(
    pembiasaan = PembiasaanStamp(checkedIn = true, time = "06:45"),
)
private val previewAllDone = AttendanceRecord(
    pembiasaan = PembiasaanStamp(checkedIn = true, time = "06:45"),
    checkout = CheckoutStamp(checkedOut = true, time = "07:58"),
)

/** Shows the stepper reacting as the student advances through the phases. */
@Preview(showBackground = true, name = "Stepper · Progression", widthDp = 360)
@Composable
private fun StatusStepperProgressionPreview() {
    FridaySTMTheme {
        Column(Modifier.padding(16.dp)) {
            StepperShowcaseRow("Fase 1 — Pembiasaan", FridayPhase.PEMBIASAAN, Activity.LARKAM, previewFresh)
            Spacer(Modifier.height(20.dp))
            StepperShowcaseRow("Fase 2 — Check-out", FridayPhase.CHECKOUT, Activity.LARKAM, previewThroughPembiasaan)
            Spacer(Modifier.height(20.dp))
            StepperShowcaseRow("Selesai", FridayPhase.DONE, Activity.LARKAM, previewAllDone)
        }
    }
}

@Preview(showBackground = true, name = "Stepper · Check-out (dark)", widthDp = 360)
@Composable
private fun StatusStepperCheckoutDarkPreview() {
    FridaySTMTheme(darkTheme = true) {
        StatusStepper(
            phase = FridayPhase.CHECKOUT,
            activeActivity = Activity.LARKAM,
            record = previewThroughPembiasaan,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Preview-only caption + stepper pairing, so each phase is easy to read. */
@Composable
private fun StepperShowcaseRow(
    caption: String,
    phase: FridayPhase,
    activeActivity: Activity?,
    record: AttendanceRecord?,
) {
    Column {
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        StatusStepper(phase = phase, activeActivity = activeActivity, record = record)
    }
}
