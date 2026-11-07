package app.aaps.pump.medtrum.compose.steps

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true, name = "Prepare - Initial")
@Composable
internal fun PreviewInitial() {
    MaterialTheme {
        PrepareStepContent(
            state = PrepareState.INITIAL,
            onNext = {}, onFilled = {}, onRetry = {}, onCancel = {}
        )
    }
}

@Preview(showBackground = true, name = "Prepare - Filled")
@Composable
internal fun PreviewFilled() {
    MaterialTheme {
        PrepareStepContent(state = PrepareState.FILLED, reservoirLevel = 185.0, onNext = {}, onFilled = {}, onRetry = {}, onCancel = {})
    }
}

@Preview(showBackground = true, name = "Prepare - Error")
@Composable
internal fun PreviewError() {
    MaterialTheme {
        PrepareStepContent(state = PrepareState.ERROR, pumpState = "STOPPED", onNext = {}, onFilled = {}, onRetry = {}, onCancel = {})
    }
}
