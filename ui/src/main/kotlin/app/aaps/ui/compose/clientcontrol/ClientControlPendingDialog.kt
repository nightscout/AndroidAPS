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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.aaps.core.interfaces.clientcontrol.ActionProgress
import app.aaps.core.interfaces.clientcontrol.FailureReason
import app.aaps.core.ui.clientcontrol.failTextResId
import app.aaps.core.interfaces.clientcontrol.PendingAction
import app.aaps.ui.R
import app.aaps.core.ui.R as CoreUiR

/**
 * Modal shown while a client-control action is in flight, and for its terminal failure states. The
 * [PendingAction] carries the already-localized action [label] (built on the initiating device) and the
 * [ActionProgress]; failures carry a [FailureReason] code mapped to a localized message **here**, so a
 * master→client failure reads in the client's locale. [ActionProgress.Applied] is handled by the caller
 * (dismiss + confirm), so it never reaches this dialog.
 *
 * Modal: back-press and tap-outside are disabled — a stray tap can't silently drop a therapy action.
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
        partial                                -> stringResource(R.string.clientcontrol_pending_partial_title)
        progress is ActionProgress.Rejected    -> stringResource(R.string.clientcontrol_pending_rejected_title)
        progress is ActionProgress.Unconfirmed -> stringResource(R.string.clientcontrol_pending_unconfirmed_title)
        else                                   -> stringResource(R.string.clientcontrol_pending_working_title) // Sending/MasterExecuting
    }

    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                verticalArrangement = Arrangement.Center
            ) {
                when (progress) {
                    is ActionProgress.Rejected    ->
                        if (partial) Text(stringResource(R.string.clientcontrol_pending_partial_format, pending.label))
                        else Text(stringResource(R.string.clientcontrol_pending_failed_format, pending.label, reasonText(progress.reason)))

                    is ActionProgress.Unconfirmed ->
                        Text(stringResource(R.string.clientcontrol_pending_unconfirmed_format, pending.label, reasonText(progress.reason)))

                    else                          ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Text(stringResource(R.string.clientcontrol_pending_working_text))
                        }
                }
            }
        },
        confirmButton = {
            if (!waiting) TextButton(onClick = onDismiss) { Text(stringResource(CoreUiR.string.ok)) }
        },
        dismissButton = {
            if (waiting) TextButton(onClick = onDismiss) { Text(stringResource(R.string.clientcontrol_pending_stop_waiting)) }
        }
    )
}

/** Localized message for a [FailureReason] code (the shared mapping; unknown codes from a newer master → generic). */
@Composable
private fun reasonText(reason: FailureReason): String = stringResource(reason.failTextResId())
