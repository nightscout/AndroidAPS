package app.aaps.pump.eopatch.compose.steps

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.eopatch.R
import app.aaps.pump.eopatch.compose.EopatchPatchViewModel

@Composable
fun SafeDeactivationStep(viewModel: EopatchPatchViewModel) {
    val isActivated by viewModel.isActivated.collectAsStateWithLifecycle()

    // Tick drives recomposition so time-dependent properties update every second
    @Suppress("UNUSED_VARIABLE")
    val tick by viewModel.expirationTick.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.updateExpirationTime()
    }

    // If not activated, the PatchScreen will close via the handleBack
    if (!isActivated) {
        LaunchedEffect(Unit) {
            viewModel.handleComplete()
        }
        return
    }

    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(R.string.string_discard_patch),
            onClick = { viewModel.changePatch() }
        ),
        secondaryButton = WizardButton(
            text = stringResource(R.string.patch_force_reset),
            onClick = { viewModel.forceResetPatchState() }
        )
    ) {
        Text(
            text = stringResource(R.string.string_discard_patch),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = stringResource(R.string.patch_safe_deactivation_desc),
            style = MaterialTheme.typography.bodyMedium
        )

        // Patch remaining info — recomputed every tick
        val remainedDays = viewModel.patchRemainedDays
        val remainedTime = viewModel.patchRemainedTime
        val remainedInsulin = viewModel.patchRemainedInsulin

        Text(
            text = stringResource(R.string.eopatch_expiration_time) + ": $remainedTime ($remainedDays ${stringResource(R.string.symbol_day)})",
            style = MaterialTheme.typography.bodyMedium
        )
        val insulinValue = when {
            remainedInsulin > 50 -> 50
            remainedInsulin < 1  -> 1
            else                 -> remainedInsulin
        }
        val insulinSymbol = if (remainedInsulin > 1) stringResource(R.string.symbol_plus) else stringResource(R.string.symbol_minus)
        Text(
            text = stringResource(app.aaps.core.ui.R.string.reservoir_label) + ": $insulinValue${stringResource(R.string.all_dose_unit)}$insulinSymbol",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Preview(showBackground = true, name = "Safe Deactivation")
@Composable
private fun SafeDeactivationStepPreview() {
    MaterialTheme {
        WizardStepLayout(
            primaryButton = WizardButton(text = "Discard Patch", onClick = {}),
            secondaryButton = WizardButton(text = "Force Reset", onClick = {})
        ) {
            Text(text = "Discard Patch", style = MaterialTheme.typography.titleLarge)
            Text(text = "To change to new Patch, the current Patch must be discarded.", style = MaterialTheme.typography.bodyMedium)
            Text(text = "Expiration time: 48:00:00 (3 day)", style = MaterialTheme.typography.bodyMedium)
            Text(text = "Reservoir: 50U+", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
