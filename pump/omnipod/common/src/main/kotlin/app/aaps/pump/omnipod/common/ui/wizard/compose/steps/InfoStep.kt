package app.aaps.pump.omnipod.common.ui.wizard.compose.steps

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.core.ui.R as CoreUiR

/**
 * Shared info step composable used by all info-only wizard steps
 * (StartActivation, PodActivated, StartDeactivation, PodDeactivated, PodDiscarded).
 */
@Composable
fun InfoStep(
    textResId: Int,
    isFinishStep: Boolean,
    onNext: () -> Unit,
    onCancel: (() -> Unit)?
) {
    InfoStepContent(
        text = stringResource(textResId),
        isFinishStep = isFinishStep,
        onNext = onNext,
        onCancel = onCancel
    )
}

/**
 * @see PreviewInfoStep
 * @see PreviewInfoStepFinish
 */
@Composable
internal fun InfoStepContent(
    text: String,
    isFinishStep: Boolean,
    onNext: () -> Unit,
    onCancel: (() -> Unit)?
) {
    WizardStepLayout(
        primaryButton = WizardButton(
            text = if (isFinishStep) stringResource(CoreUiR.string.finish)
            else stringResource(CoreUiR.string.next),
            onClick = onNext
        ),
        secondaryButton = if (onCancel != null) WizardButton(
            text = stringResource(CoreUiR.string.cancel),
            onClick = onCancel
        ) else null
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
