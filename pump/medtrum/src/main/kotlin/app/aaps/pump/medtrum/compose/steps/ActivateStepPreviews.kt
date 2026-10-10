package app.aaps.pump.medtrum.compose.steps

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true, name = "Activate - Activating")
@Composable
internal fun PreviewActivating() {
    MaterialTheme {
        ActivateStepContent(state = ActivateState.ACTIVATING, onComplete = {}, onCancel = {})
    }
}

@Preview(showBackground = true, name = "Activate - Complete")
@Composable
internal fun ActivateStepPreviewComplete() {
    MaterialTheme {
        ActivateStepContent(state = ActivateState.COMPLETE, reservoirLevel = 200.0, onComplete = {}, onCancel = {})
    }
}
