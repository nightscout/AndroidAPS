package app.aaps.ui.compose.scenes.wizard

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings

/**
 * @see InfoStepExercisePreview
 * @see InfoStepSickDayPreview
 * @see InfoStepSleepPreview
 * @see InfoStepPreMealPreview
 * @see InfoStepBathingPreview
 * @see InfoStepAlcoholPreview
 * @see InfoStepDrivingPreview
 * @see InfoStepSickDayVomitingPreview
 * @see InfoStepLutealPhasePreview
 * @see InfoStepHotWeatherPreview
 * @see InfoStepMedicalProcedurePreview
 */
@Composable
internal fun InfoStep(
    state: SceneWizardViewModel.WizardState,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val template = state.template ?: return
    WizardStepLayout(
        secondaryButton = WizardButton(text = stringResource(CoreUiStrings.back), onClick = onBack),
        primaryButton = WizardButton(text = stringResource(CoreUiStrings.next), onClick = onNext)
    ) {
        Text(
            text = stringResource(template.nameResId),
            style = MaterialTheme.typography.headlineSmall
        )
        template.infoResId?.let { info ->
            Text(
                text = stringResource(info),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
