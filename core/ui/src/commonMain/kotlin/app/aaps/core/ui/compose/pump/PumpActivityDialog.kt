package app.aaps.core.ui.compose.pump

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.aaps.core.interfaces.pump.BolusProgressState
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.stringResource

/**
 * Shared pump activity dialog showing pump status, queue info, and bolus progress.
 *
 * Two display modes:
 * - Modal (standard bolus): auto-opened, full scrim, not dismissable
 * - Non-modal (SMB / other): opened via FAB tap, dismissable
 */
@Composable
fun PumpActivityDialog(
    bolusState: BolusProgressState?,
    pumpStatus: String,
    queueStatus: AnnotatedString?,
    isModal: Boolean,
    onStop: () -> Unit,
    onDismiss: () -> Unit
) {
    if (isModal) {
        // Modal (standard bolus): a real Dialog, so it gets its own window and the app behind it
        // leaves the accessibility tree.
        //
        // This used to be a Box with a scrim and a clickable that only consumed TOUCHES. That
        // blocks a finger, but a screen reader does not use touches - it activates controls
        // through accessibility actions, which a touch handler does not stop - and a sibling Box
        // removes nothing from the semantics tree. So during a bolus a blind user was never told
        // delivery had started and could still reach and operate the UI behind the scrim,
        // including starting a second treatment.
        //
        // Not dismissable, as before: no dismiss on back press or on a tap outside, and the
        // dialog goes away only when the caller stops composing it. usePlatformDefaultWidth is
        // off so the card keeps the full-bleed width it had inside the Box.
        Dialog(
            onDismissRequest = { },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            PumpActivityCard(
                bolusState = bolusState,
                pumpStatus = pumpStatus,
                queueStatus = queueStatus,
                onStop = onStop,
                onDismiss = onDismiss
            )
        }
    } else {
        // Non-modal: standard dialog, dismissable
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
        ) {
            PumpActivityCard(
                bolusState = bolusState,
                pumpStatus = pumpStatus,
                queueStatus = queueStatus,
                onStop = onStop,
                onDismiss = onDismiss
            )
        }
    }
}

/**
 * @see PreviewBolusInProgress
 * @see PreviewBolusStopPressed
 * @see PreviewBolusCompleted
 * @see PreviewBolusIndeterminate
 * @see PreviewBolusStalled
 * @see PreviewPumpStatusOnly
 */
@Composable
internal fun PumpActivityCard(
    bolusState: BolusProgressState?,
    pumpStatus: String,
    queueStatus: AnnotatedString?,
    onStop: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AapsSpacing.xxLarge),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(AapsSpacing.xxLarge),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Bolus progress section
            if (bolusState != null) {
                BolusProgressSection(
                    state = bolusState,
                    onStop = onStop,
                    onDismiss = onDismiss
                )
            }

            // Hide pump/queue status once delivery progress starts (percent > 0)
            // — the progress section already shows all needed info
            val hideStatus = bolusState != null && bolusState.percent > 0

            // Pump status section
            if (!hideStatus && pumpStatus.isNotEmpty()) {
                if (bolusState != null) Spacer(modifier = Modifier.height(AapsSpacing.extraLarge))
                Text(
                    text = pumpStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Queue status section
            if (!hideStatus && queueStatus != null) {
                Spacer(modifier = Modifier.height(AapsSpacing.medium))
                Text(
                    text = queueStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun BolusProgressSection(
    state: BolusProgressState,
    onStop: () -> Unit,
    onDismiss: () -> Unit
) {
    // Title
    Text(
        text = stringResource(CoreUiStrings.goingtodeliver, state.insulin),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(AapsSpacing.large))

    // Status text — hidden when stalled: a present-tense "Delivering …" line would contradict the
    // "connection lost / status unknown" message and read as if delivery were still being tracked.
    val statusText = stringResource(state.status)
    if (statusText.isNotEmpty() && !state.stalled) {
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            // Deliberately NOT a liveRegion. This line is rebuilt on every progress frame -
            // BolusProgressData.updateProgress puts the delivered amount into a whole sentence, and
            // the Dana drivers call it per 0.01 U - so a live region here fires as often as Compose
            // allows (one per 100 ms). TalkBack queues polite announcements, so the user would get
            // a growing backlog of "Delivering 1.23 U, Delivering 1.24 U ..." and could not hear
            // anything else, including the Stop button. The amount is on screen to be read on
            // demand; it does not need to interrupt.
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(AapsSpacing.large))
    }

    // Progress bar — hidden when stalled so a frozen (or still-animating indeterminate) bar isn't read
    // as live delivery; the last-known percent is no longer authoritative once the stream is lost.
    if (!state.stalled) {
        if (state.percent > 0) {
            LinearProgressIndicator(
                progress = { state.percent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AapsSpacing.medium),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        } else {
            // Indeterminate when no progress received yet
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AapsSpacing.medium),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(AapsSpacing.extraLarge))
    }

    when {
        // Stalled (client/follower only — never set for a local bolus): the progress stream stopped
        // before a terminal frame. Stop can't reach the master either, so offer a manual dismiss that
        // only hides this dialog — it does NOT stop the pump.
        state.stalled -> {
            val stalledTitle = stringResource(CoreUiStrings.clientcontrol_bolus_progress_stalled_title)
            // paneTitle, NOT liveRegion. This whole branch is composed for the first time at the
            // moment of the stall, and Compose only fires a live region for a node that was already
            // in the tree on the previous pass - sendSemanticsPropertyChangeEvents skips a node with
            // no previous entry - so a liveRegion here announces nothing at all. The same flip also
            // removes the running status line, which was the only node still speaking, so the moment
            // we lose track of a bolus would otherwise be met with silence. A newly appearing
            // paneTitle is the supported way to say "this just appeared": it sends
            // CONTENT_CHANGE_TYPE_PANE_APPEARED carrying the title.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { paneTitle = stalledTitle }
            ) {
                Text(
                    text = stalledTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(AapsSpacing.medium))
                Text(
                    text = stringResource(CoreUiStrings.clientcontrol_bolus_progress_stalled_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(AapsSpacing.large))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                // Neutral/tonal — Dismiss only hides this local view; it is NOT destructive like Stop,
                // so it must not borrow Stop's error-red affordance.
                FilledTonalButton(onClick = onDismiss) {
                    Text(text = stringResource(CoreUiStrings.dismiss))
                }
            }
        }

        // Stop button (delivery in progress)
        state.percent < 100 -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = onStop,
                    enabled = !state.stopPressed && state.stopDeliveryEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text(
                        text = if (state.stopPressed) stringResource(CoreUiStrings.stop_pressed)
                        else stringResource(CoreUiStrings.stop)
                    )
                }
            }
        }
    }
}

// --- Previews ---
