package app.aaps.pump.medtrum.compose.steps

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true, name = "Prime - Ready")
@Composable
internal fun PreviewReady() {
    MaterialTheme {
        PrimeStepContent(state = PrimeState.READY, onStartPrime = {}, onNext = {}, onCancel = {})
    }
}

@Preview(showBackground = true, name = "Prime - Priming")
@Composable
internal fun PreviewPriming() {
    MaterialTheme {
        PrimeStepContent(state = PrimeState.PRIMING, primeProgress = 75, onStartPrime = {}, onNext = {}, onCancel = {})
    }
}

@Preview(showBackground = true, name = "Prime - Complete")
@Composable
internal fun PrimeStepPreviewComplete() {
    MaterialTheme {
        PrimeStepContent(state = PrimeState.COMPLETE, onStartPrime = {}, onNext = {}, onCancel = {})
    }
}
