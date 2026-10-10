package app.aaps.pump.eopatch.compose.steps

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.eopatch.R

@Preview(showBackground = true, name = "Rotate Knob")
@Composable
internal fun RotateKnobStepPreview() {
    MaterialTheme {
        WizardStepLayout(
            primaryButton = WizardButton(text = "Next", onClick = {})
        ) {
            Text(text = stringResource(R.string.patch_rotate_knob), style = MaterialTheme.typography.titleLarge)
            Text(text = stringResource(R.string.patch_rotate_knob_desc_1), style = MaterialTheme.typography.bodyMedium)
            Text(text = stringResource(R.string.patch_rotate_knob_desc_2), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Preview(showBackground = true, name = "Rotate Knob - Needle Error")
@Composable
internal fun RotateKnobNeedleErrorPreview() {
    MaterialTheme {
        WizardStepLayout(
            primaryButton = WizardButton(text = "Retry", onClick = {})
        ) {
            Text(text = stringResource(R.string.patch_rotate_knob), style = MaterialTheme.typography.titleLarge)
            Text(text = stringResource(R.string.patch_rotate_knob_desc_1), style = MaterialTheme.typography.bodyMedium)
            Text(text = stringResource(R.string.patch_rotate_knob_desc_2_needle_insertion_error), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
