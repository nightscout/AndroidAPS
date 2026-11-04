package app.aaps.pump.eopatch.compose.steps

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.eopatch.R

@Preview(showBackground = true, name = "Remove Protection Tape")
@Composable
internal fun RemoveProtectionTapeStepPreview() {
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
