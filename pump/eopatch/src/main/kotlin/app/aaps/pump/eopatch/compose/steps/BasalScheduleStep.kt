package app.aaps.pump.eopatch.compose.steps

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.eopatch.R
import app.aaps.pump.eopatch.compose.EopatchPatchViewModel

@Composable
fun BasalScheduleStep(viewModel: EopatchPatchViewModel) {
    LaunchedEffect(Unit) {
        viewModel.initPatchStep()
    }

    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.finish),
            onClick = { viewModel.onConfirm() }
        )
    ) {
        Text(
            text = stringResource(R.string.patch_basal_schedule),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = viewModel.programEnabledMessage,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = stringResource(R.string.patch_basal_schedule_desc_2),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Preview(showBackground = true, name = "Basal Schedule Complete")
@Composable
private fun BasalScheduleStepPreview() {
    MaterialTheme {
        WizardStepLayout(
            primaryButton = WizardButton(text = "Finish", onClick = {})
        ) {
            Text(text = "Patch activation completed!", style = MaterialTheme.typography.titleLarge)
            Text(text = "'Basal 1' program has been enabled.", style = MaterialTheme.typography.bodyMedium)
            Text(text = "Alerts you when the Patch nears its expiration time.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
