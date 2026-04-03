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
fun TurningOffAlarmStep(viewModel: EopatchPatchViewModel) {
    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.next),
            onClick = { viewModel.onConfirm() }
        )
    ) {
        Text(
            text = stringResource(R.string.patch_manually_turning_off_alarm_title),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = stringResource(R.string.patch_manually_turning_off_alarm_desc_1),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = stringResource(R.string.patch_manually_turning_off_alarm_desc_2),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = stringResource(R.string.patch_manually_turning_off_alarm_step_1),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = stringResource(R.string.patch_manually_turning_off_alarm_step_2),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Preview(showBackground = true, name = "Turning Off Alarm")
@Composable
private fun TurningOffAlarmStepPreview() {
    MaterialTheme {
        WizardStepLayout(
            primaryButton = WizardButton(text = stringResource(app.aaps.core.ui.R.string.next), onClick = {})
        ) {
            Text(text = stringResource(R.string.patch_manually_turning_off_alarm_title), style = MaterialTheme.typography.titleLarge)
            Text(text = stringResource(R.string.patch_manually_turning_off_alarm_desc_1), style = MaterialTheme.typography.bodyMedium)
            Text(text = stringResource(R.string.patch_manually_turning_off_alarm_desc_2), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
