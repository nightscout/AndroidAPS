package app.aaps.pump.eopatch.compose.steps

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.eopatch.R
import app.aaps.pump.eopatch.compose.EopatchPatchViewModel

@Composable
fun RemoveStep(viewModel: EopatchPatchViewModel) {
    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.confirm),
            onClick = { viewModel.onConfirm() }
        )
    ) {
        Text(
            text = stringResource(R.string.patch_discard_complete_title),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = stringResource(R.string.patch_discard_complete_desc),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Preview(showBackground = true, name = "Remove Step - Discard Complete")
@Composable
private fun RemoveStepPreview() {
    MaterialTheme {
        WizardStepLayout(
            primaryButton = WizardButton(text = stringResource(app.aaps.core.ui.R.string.confirm), onClick = {})
        ) {
            Text(text = stringResource(R.string.patch_discard_complete_title), style = MaterialTheme.typography.titleLarge)
            Text(text = stringResource(R.string.patch_discard_complete_desc), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
