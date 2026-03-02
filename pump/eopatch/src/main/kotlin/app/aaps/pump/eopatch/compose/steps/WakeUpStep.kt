package app.aaps.pump.eopatch.compose.steps

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.eopatch.R
import app.aaps.pump.eopatch.code.PatchStep
import app.aaps.pump.eopatch.compose.EopatchPatchViewModel

@Composable
fun WakeUpStep(viewModel: EopatchPatchViewModel) {
    LaunchedEffect(Unit) {
        viewModel.initPatchStep()
    }

    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(R.string.patch_start_pairing),
            onClick = { viewModel.moveStep(PatchStep.CONNECT_NEW) }
        ),
        secondaryButton = WizardButton(
            text = stringResource(R.string.cancel),
            onClick = { viewModel.moveStep(PatchStep.CANCEL) }
        )
    ) {
        Text(
            text = stringResource(R.string.patch_wake_up),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = stringResource(R.string.patch_wake_up_desc_1),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = stringResource(R.string.patch_wake_up_desc_2),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
