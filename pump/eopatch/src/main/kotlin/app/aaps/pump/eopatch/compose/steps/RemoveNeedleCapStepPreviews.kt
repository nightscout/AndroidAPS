package app.aaps.pump.eopatch.compose.steps

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.eopatch.R

@Preview(showBackground = true, name = "Remove Needle Cap")
@Composable
internal fun RemoveNeedleCapStepPreview() {
    MaterialTheme {
        RemoveNeedleCapStep_Content()
    }
}

@Composable
private fun RemoveNeedleCapStep_Content() {
    WizardStepLayout(
        primaryButton = WizardButton(text = stringResource(app.aaps.core.ui.R.string.next), onClick = {}),
        secondaryButton = WizardButton(text = stringResource(app.aaps.core.ui.R.string.discard), onClick = {})
    ) {
        Text(text = stringResource(R.string.patch_remove_needle_cap), style = MaterialTheme.typography.titleLarge)
        Text(text = stringResource(R.string.patch_remove_needle_cap_desc_1), style = MaterialTheme.typography.bodyMedium)
        Text(text = stringResource(R.string.patch_remove_needle_cap_desc_2), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        Text(text = stringResource(R.string.patch_remove_needle_cap_desc_3), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
}
