package app.aaps.ui.compose.scenes.wizard

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.ui.compose.scenes.SceneTemplate

@Preview(showBackground = true, showSystemUi = true)
@Composable
internal fun InfoStepExercisePreview() {
    MaterialTheme {
        InfoStep(state = previewState.copy(template = SceneTemplate.EXERCISE), onBack = {}, onNext = {})
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
internal fun InfoStepSickDayPreview() {
    MaterialTheme {
        InfoStep(state = previewState.copy(template = SceneTemplate.SICK_DAY), onBack = {}, onNext = {})
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
internal fun InfoStepSleepPreview() {
    MaterialTheme {
        InfoStep(state = previewState.copy(template = SceneTemplate.SLEEP), onBack = {}, onNext = {})
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
internal fun InfoStepPreMealPreview() {
    MaterialTheme {
        InfoStep(state = previewState.copy(template = SceneTemplate.PRE_MEAL), onBack = {}, onNext = {})
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
internal fun InfoStepBathingPreview() {
    MaterialTheme {
        InfoStep(state = previewState.copy(template = SceneTemplate.BATHING), onBack = {}, onNext = {})
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
internal fun InfoStepAlcoholPreview() {
    MaterialTheme {
        InfoStep(state = previewState.copy(template = SceneTemplate.ALCOHOL), onBack = {}, onNext = {})
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
internal fun InfoStepDrivingPreview() {
    MaterialTheme {
        InfoStep(state = previewState.copy(template = SceneTemplate.DRIVING), onBack = {}, onNext = {})
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
internal fun InfoStepSickDayVomitingPreview() {
    MaterialTheme {
        InfoStep(state = previewState.copy(template = SceneTemplate.SICK_DAY_VOMITING), onBack = {}, onNext = {})
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
internal fun InfoStepLutealPhasePreview() {
    MaterialTheme {
        InfoStep(state = previewState.copy(template = SceneTemplate.LUTEAL_PHASE), onBack = {}, onNext = {})
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
internal fun InfoStepHotWeatherPreview() {
    MaterialTheme {
        InfoStep(state = previewState.copy(template = SceneTemplate.HOT_WEATHER), onBack = {}, onNext = {})
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
internal fun InfoStepMedicalProcedurePreview() {
    MaterialTheme {
        InfoStep(state = previewState.copy(template = SceneTemplate.MEDICAL_PROCEDURE), onBack = {}, onNext = {})
    }
}
