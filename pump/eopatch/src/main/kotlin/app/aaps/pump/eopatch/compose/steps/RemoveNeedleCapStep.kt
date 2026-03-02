package app.aaps.pump.eopatch.compose.steps

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.eopatch.R
import app.aaps.pump.eopatch.code.PatchStep
import app.aaps.pump.eopatch.compose.EopatchPatchViewModel

@Composable
fun RemoveNeedleCapStep(viewModel: EopatchPatchViewModel) {
    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(R.string.next),
            onClick = { viewModel.moveStep(PatchStep.REMOVE_PROTECTION_TAPE) }
        ),
        secondaryButton = WizardButton(
            text = stringResource(R.string.discard),
            onClick = { viewModel.discardPatchWithCommCheck() }
        )
    ) {
        Text(
            text = stringResource(R.string.patch_remove_needle_cap),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = stringResource(R.string.patch_remove_needle_cap_desc_1),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = stringResource(R.string.patch_remove_needle_cap_desc_2),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        Text(
            text = stringResource(R.string.patch_remove_needle_cap_desc_3),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
}
