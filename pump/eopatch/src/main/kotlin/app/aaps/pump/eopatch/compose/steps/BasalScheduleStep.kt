package app.aaps.pump.eopatch.compose.steps

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
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
            text = stringResource(R.string.finish),
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
