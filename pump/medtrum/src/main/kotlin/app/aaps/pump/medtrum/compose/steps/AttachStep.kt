package app.aaps.pump.medtrum.compose.steps

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.medtrum.R
import app.aaps.pump.medtrum.code.PatchStep
import app.aaps.pump.medtrum.compose.MedtrumPatchViewModel

@Composable
fun AttachStep(
    viewModel: MedtrumPatchViewModel,
    onCancel: () -> Unit
) {
    AttachStepContent(
        onNext = { viewModel.moveStep(PatchStep.ACTIVATE) },
        onCancel = onCancel
    )
}

@Composable
internal fun AttachStepContent(
    onNext: () -> Unit,
    onCancel: () -> Unit
) {
    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.next),
            onClick = onNext
        ),
        secondaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.cancel),
            onClick = onCancel
        )
    ) {
        Text(
            text = stringResource(R.string.remove_safety_lock),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.press_next_to_start_activation).stripHtml(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true, name = "Attach Step")
@Composable
private fun PreviewAttachStep() {
    MaterialTheme {
        AttachStepContent(onNext = {}, onCancel = {})
    }
}
