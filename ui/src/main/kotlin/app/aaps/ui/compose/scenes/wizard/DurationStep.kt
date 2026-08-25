package app.aaps.ui.compose.scenes.wizard

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import app.aaps.core.data.configuration.Constants
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.NumberInputRow
import app.aaps.core.ui.compose.formatMinutesAsDuration
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.core.interfaces.R as InterfacesR

/**
 * @see DurationStepPreview
 */
@Composable
internal fun DurationStep(
    state: SceneWizardViewModel.WizardState,
    onSetDuration: (Int) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    WizardStepLayout(
        secondaryButton = WizardButton(
            text = stringResource(R.string.back),
            onClick = { focusManager.clearFocus(); onBack() }
        ),
        primaryButton = WizardButton(
            text = stringResource(R.string.next),
            onClick = { focusManager.clearFocus(); onNext() }
        )
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
            valueRange = Constants.SCENE_DURATION,
            step = 5.0,
            valueFormatRef = TextRef.AndroidRes(InterfacesR.string.mins),
            formatAsInt = true,
            displayValue = when {
                state.durationMinutes == 0 -> stringResource(R.string.scene_duration_indefinite)
                else                       -> formatMinutesAsDuration(state.durationMinutes)
            }
        )
    }
}
