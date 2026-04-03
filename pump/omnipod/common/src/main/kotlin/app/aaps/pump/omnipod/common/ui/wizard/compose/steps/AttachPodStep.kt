package app.aaps.pump.omnipod.common.ui.wizard.compose.steps

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.omnipod.common.R
import app.aaps.core.ui.R as CoreUiR

/**
 * AttachPod info step with a confirmation dialog before proceeding to cannula insertion.
 * Matches the existing behavior in AttachPodFragment.
 *
 * @param titleResId Title string resource (also used as dialog title)
 * @param textResId Body text string resource
 * @param onNext Called when user confirms to proceed
 * @param onCancel Called when Cancel is pressed
 */
@Composable
fun AttachPodStep(
    titleResId: Int,
    textResId: Int,
    onNext: () -> Unit,
    onCancel: () -> Unit
) {
    var showConfirmDialog by remember { mutableStateOf(false) }

    if (showConfirmDialog) {
        OkCancelDialog(
            title = stringResource(titleResId),
            message = stringResource(R.string.omnipod_common_pod_activation_wizard_attach_pod_confirm_insert_cannula_text),
            onConfirm = {
                showConfirmDialog = false
                onNext()
            },
            onDismiss = { showConfirmDialog = false }
        )
    }

    AttachPodStepContent(
        text = stringResource(textResId),
        onNext = { showConfirmDialog = true },
        onCancel = onCancel
    )
}

@Composable
internal fun AttachPodStepContent(
    text: String,
    onNext: () -> Unit,
    onCancel: () -> Unit
) {
    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(CoreUiR.string.next),
            onClick = onNext
        ),
        secondaryButton = WizardButton(
            text = stringResource(CoreUiR.string.cancel),
            onClick = onCancel
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Preview(showBackground = true, name = "Attach Pod")
@Composable
private fun PreviewAttachPod() {
    MaterialTheme {
        AttachPodStepContent(
            text = "Fill the new pod with insulin. You will hear two beeps when the pod is ready.",
            onNext = {},
            onCancel = {}
        )
    }
}
