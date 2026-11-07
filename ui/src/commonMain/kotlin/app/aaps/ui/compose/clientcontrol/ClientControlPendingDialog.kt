package app.aaps.ui.compose.clientcontrol

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.aaps.core.interfaces.clientcontrol.ActionProgress
import app.aaps.core.interfaces.clientcontrol.FailureReason
import app.aaps.core.interfaces.clientcontrol.PendingAction
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.clientcontrol.failText
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings

/**
 * Modal shown while a client-control action is in flight, and for its terminal failure states. The
 * [PendingAction] carries the already-localized action [label] (built on the initiating device) and the
 * [ActionProgress]; failures carry a [FailureReason] code mapped to a localized message **here**, so a
 * master→client failure reads in the client's locale. [ActionProgress.Applied] is handled by the caller
 * (dismiss + confirm), so it never reaches this dialog.
 *
 * Modal: back-press and tap-outside are disabled — a stray tap can't silently drop a therapy action.
 *
 * The dialog changes state in place, so the body line is one node in every state and is an assertive
 * live region once an outcome arrives: a screen-reader user hears that the action was refused even
 * though nothing appeared or moved on screen.
 */
@Composable
fun ClientControlPendingDialog(
    pending: PendingAction,
    onDismiss: () -> Unit
) {
    val progress = pending.progress
    // Sending and MasterExecuting share one visual ("working") so the dialog doesn't resize mid-flight.
    val waiting = progress is ActionProgress.Sending || progress is ActionProgress.MasterExecuting

    // A partial failure (e.g. a chained scene where some target actions failed) mostly succeeded — don't
    // dress it up as a flat rejection.
    val partial = progress is ActionProgress.Rejected && progress.reason == FailureReason.PartialFailure

    val title = when {
        partial                                -> stringResource(UiStrings.clientcontrol_pending_partial_title)
        progress is ActionProgress.Rejected    -> stringResource(UiStrings.clientcontrol_pending_rejected_title)
        progress is ActionProgress.Unconfirmed -> stringResource(UiStrings.clientcontrol_pending_unconfirmed_title)
        else                                   -> stringResource(UiStrings.clientcontrol_pending_working_title) // Sending/MasterExecuting
    }

    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(title) },
        text = {
            // ONE body text for every state, picked here, so that the Text node below is composed in
            // every state and only its content changes. That is what makes the live region speak:
            // Compose announces a live region as a side effect of a property change on a node that
            // was already in the tree on the previous pass, and skips a node that has just appeared.
            // The older shape composed a different Text per outcome branch, so the outcome node
            // appeared instead of changing and nothing was announced at all - a blind user sent a
            // bolus, heard "waiting", and was never told that the master had refused it.
            val bodyText = when {
                partial                                -> stringResource(UiStrings.clientcontrol_pending_partial_format, pending.label)
                progress is ActionProgress.Rejected    -> stringResource(UiStrings.clientcontrol_pending_failed_format, pending.label, reasonText(progress.reason))
                progress is ActionProgress.Unconfirmed -> stringResource(UiStrings.clientcontrol_pending_unconfirmed_format, pending.label, reasonText(progress.reason))
                else                                   -> stringResource(UiStrings.clientcontrol_pending_working_text) // Sending/MasterExecuting
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                verticalArrangement = Arrangement.Center
            ) {
                // The spinner is the only part that comes and goes; it sits in its own conditional
                // group, so the Text after it keeps its slot - and with it its semantics node.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (waiting) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text(
                        text = bodyText,
                        // Assertive on an outcome: the action was refused, only partly applied, or
                        // could not be confirmed, and the user must not miss that. Polite while
                        // waiting, where the text never changes, so the routine state never speaks.
                        modifier = Modifier.semantics {
                            liveRegion = if (waiting) LiveRegionMode.Polite else LiveRegionMode.Assertive
                        }
                    )
                }
            }
        },
        confirmButton = {
            if (!waiting) TextButton(onClick = onDismiss) { Text(stringResource(CoreUiStrings.ok)) }
        },
        dismissButton = {
            if (waiting) TextButton(onClick = onDismiss) { Text(stringResource(UiStrings.clientcontrol_pending_stop_waiting)) }
        }
    )
}

/** Localized message for a [FailureReason] code (the shared mapping; unknown codes from a newer master → generic). */
@Composable
private fun reasonText(reason: FailureReason): String = stringResource(reason.failText())
