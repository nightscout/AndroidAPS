package app.aaps.pump.eopatch.compose.steps

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.insulin.SelectInsulin
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.eopatch.code.PatchStep
import app.aaps.pump.eopatch.compose.EopatchPatchViewModel

@Composable
fun SelectInsulinStep(viewModel: EopatchPatchViewModel) {
    val availableInsulins by viewModel.availableInsulins.collectAsStateWithLifecycle()
    val selectedInsulin by viewModel.selectedInsulin.collectAsStateWithLifecycle()
    val activeInsulinLabel by viewModel.activeInsulinLabel.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadInsulins()
    }

    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.next),
            onClick = { viewModel.moveStep(PatchStep.REMOVE_NEEDLE_CAP) }
        ),
        secondaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.cancel),
            onClick = { viewModel.moveStep(PatchStep.CANCEL) }
        )
    ) {
        Text(
            text = stringResource(app.aaps.core.ui.R.string.select_insulin_description),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(16.dp))
        SelectInsulin(
            availableInsulins = availableInsulins,
            selectedInsulin = selectedInsulin,
            activeInsulinLabel = activeInsulinLabel,
            onInsulinSelect = viewModel::selectInsulin
        )
    }
}
