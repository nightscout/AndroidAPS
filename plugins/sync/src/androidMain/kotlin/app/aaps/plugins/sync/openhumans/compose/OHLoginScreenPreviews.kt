package app.aaps.plugins.sync.openhumans.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true, name = "Welcome")
@Composable
internal fun WelcomeStepPreview() {
    MaterialTheme {
        WelcomeStep(onNext = {})
    }
}

@Preview(showBackground = true, name = "Consent")
@Composable
internal fun ConsentStepPreview() {
    MaterialTheme {
        ConsentStep(authUrl = "https://example.com/auth")
    }
}

@Preview(showBackground = true, name = "Confirm")
@Composable
internal fun ConfirmStepPreview() {
    MaterialTheme {
        ConfirmStep(onCancel = {}, onProceed = {})
    }
}

@Preview(showBackground = true, name = "Finishing")
@Composable
internal fun FinishingStepPreview() {
    MaterialTheme {
        FinishingStep()
    }
}

@Preview(showBackground = true, name = "Done")
@Composable
internal fun DoneStepPreview() {
    MaterialTheme {
        DoneStep(onClose = {})
    }
}
