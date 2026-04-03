package app.aaps.pump.eopatch.compose.steps

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.eopatch.R
import app.aaps.pump.eopatch.code.PatchStep
import app.aaps.pump.eopatch.compose.EopatchPatchViewModel

@Composable
fun RemoveProtectionTapeStep(viewModel: EopatchPatchViewModel) {
    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(R.string.patch_start_safety_check),
            onClick = { viewModel.moveStep(PatchStep.SAFETY_CHECK) }
        )
    ) {
        Text(
            text = stringResource(R.string.patch_remove_protection_tape),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = stringResource(R.string.patch_remove_protection_tape_desc_1),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = stringResource(R.string.patch_remove_protection_tape_desc_2),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Preview(showBackground = true, name = "Remove Protection Tape")
@Composable
private fun RemoveProtectionTapeStepPreview() {
    MaterialTheme {
        WizardStepLayout(
            primaryButton = WizardButton(text = stringResource(R.string.patch_start_safety_check), onClick = {})
        ) {
            Text(text = stringResource(R.string.patch_remove_protection_tape), style = MaterialTheme.typography.titleLarge)
            Text(text = stringResource(R.string.patch_remove_protection_tape_desc_1), style = MaterialTheme.typography.bodyMedium)
            Text(text = stringResource(R.string.patch_remove_protection_tape_desc_2), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
