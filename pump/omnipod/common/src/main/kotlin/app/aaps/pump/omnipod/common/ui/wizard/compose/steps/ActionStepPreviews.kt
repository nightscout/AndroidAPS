package app.aaps.pump.omnipod.common.ui.wizard.compose.steps

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.pump.omnipod.common.ui.wizard.compose.ActionState

@Preview(showBackground = true, name = "Action - Executing")
@Composable
internal fun PreviewExecuting() {
    MaterialTheme {
        ActionStepContent(
            actionState = ActionState.Executing,
            text = "Initializing pod...",
            onNext = {}, onRetry = {}, onCancel = {}
        )
    }
}

@Preview(showBackground = true, name = "Action - Success")
@Composable
internal fun PreviewSuccess() {
    MaterialTheme {
        ActionStepContent(
            actionState = ActionState.Idle, // Idle used as stand-in for Success in preview
            text = "Pod initialized successfully.",
            onNext = {}, onRetry = {}, onCancel = {}
        )
    }
}

@Preview(showBackground = true, name = "Action - Error")
@Composable
internal fun PreviewError() {
    MaterialTheme {
        ActionStepContent(
            actionState = ActionState.Error("Communication error. Please retry."),
            text = "Initializing pod...",
            onNext = {}, onRetry = {}, onCancel = {}
        )
    }
}

@Preview(showBackground = true, name = "Action - Error with Deactivate")
@Composable
internal fun PreviewErrorDeactivate() {
    MaterialTheme {
        ActionStepContent(
            actionState = ActionState.Error("Pod alarm triggered."),
            text = "Initializing pod...",
            showDeactivateButton = true,
            onNext = {}, onRetry = {}, onCancel = {}, onDeactivatePod = {}
        )
    }
}
