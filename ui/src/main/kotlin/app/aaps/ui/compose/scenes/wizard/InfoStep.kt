package app.aaps.ui.compose.scenes.wizard

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.ui.compose.scenes.SceneTemplate

@Composable
internal fun InfoStep(
    state: SceneWizardViewModel.WizardState,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val template = state.template ?: return
    WizardStepLayout(
        secondaryButton = WizardButton(text = stringResource(R.string.back), onClick = onBack),
        primaryButton = WizardButton(text = stringResource(R.string.next), onClick = onNext)
    ) {
        Text(
            text = stringResource(template.nameResId),
            style = MaterialTheme.typography.headlineSmall
        )
        if (template.infoResId != 0) {
            Text(
                text = stringResource(template.infoResId),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun InfoStepExercisePreview() {
    MaterialTheme {
        InfoStep(state = previewState.copy(template = SceneTemplate.EXERCISE), onBack = {}, onNext = {})
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun InfoStepSickDayPreview() {
    MaterialTheme {
        InfoStep(state = previewState.copy(template = SceneTemplate.SICK_DAY), onBack = {}, onNext = {})
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun InfoStepSleepPreview() {
    MaterialTheme {
        InfoStep(state = previewState.copy(template = SceneTemplate.SLEEP), onBack = {}, onNext = {})
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun InfoStepPreMealPreview() {
    MaterialTheme {
        InfoStep(state = previewState.copy(template = SceneTemplate.PRE_MEAL), onBack = {}, onNext = {})
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun InfoStepBathingPreview() {
    MaterialTheme {
        InfoStep(state = previewState.copy(template = SceneTemplate.BATHING), onBack = {}, onNext = {})
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun InfoStepAlcoholPreview() {
    MaterialTheme {
        InfoStep(state = previewState.copy(template = SceneTemplate.ALCOHOL), onBack = {}, onNext = {})
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun InfoStepDrivingPreview() {
    MaterialTheme {
        InfoStep(state = previewState.copy(template = SceneTemplate.DRIVING), onBack = {}, onNext = {})
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun InfoStepSickDayVomitingPreview() {
    MaterialTheme {
        InfoStep(state = previewState.copy(template = SceneTemplate.SICK_DAY_VOMITING), onBack = {}, onNext = {})
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun InfoStepLutealPhasePreview() {
    MaterialTheme {
        InfoStep(state = previewState.copy(template = SceneTemplate.LUTEAL_PHASE), onBack = {}, onNext = {})
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun InfoStepHotWeatherPreview() {
    MaterialTheme {
        InfoStep(state = previewState.copy(template = SceneTemplate.HOT_WEATHER), onBack = {}, onNext = {})
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun InfoStepMedicalProcedurePreview() {
    MaterialTheme {
        InfoStep(state = previewState.copy(template = SceneTemplate.MEDICAL_PROCEDURE), onBack = {}, onNext = {})
    }
}
