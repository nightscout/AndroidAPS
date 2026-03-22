package app.aaps.ui.compose.scenes.wizard

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.NumberInputRow
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout

@Composable
internal fun DurationStep(
    state: SceneWizardViewModel.WizardState,
    onSetDuration: (Int) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    WizardStepLayout(
        secondaryButton = WizardButton(text = stringResource(R.string.back), onClick = onBack),
        primaryButton = WizardButton(text = stringResource(R.string.next), onClick = onNext)
    ) {
        Text(
            text = stringResource(R.string.duration),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = stringResource(R.string.scene_wizard_duration_info),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        NumberInputRow(
            labelResId = R.string.duration,
            value = state.durationMinutes.toDouble(),
            onValueChange = { onSetDuration(it.toInt()) },
            valueRange = 0.0..4320.0,
            step = 5.0,
            controlPoints = listOf(
                0.0 to 0.0,
                0.3 to 60.0,
                0.6 to 480.0,
                0.85 to 1440.0,
                1.0 to 4320.0
            ),
            valueFormatResId = R.string.mins,
            formatAsInt = true
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun DurationStepPreview() {
    MaterialTheme {
        DurationStep(
            state = previewState,
            onSetDuration = {},
            onBack = {}, onNext = {}
        )
    }
}
