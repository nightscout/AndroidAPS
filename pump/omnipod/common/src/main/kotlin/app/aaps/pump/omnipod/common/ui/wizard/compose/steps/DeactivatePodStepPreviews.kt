package app.aaps.pump.omnipod.common.ui.wizard.compose.steps

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.pump.omnipod.common.ui.wizard.compose.ActionState

@Preview(showBackground = true, name = "Deactivate - In Progress")
@Composable
internal fun PreviewDeactivating() {
    MaterialTheme {
        DeactivatePodStepContent(
            actionState = ActionState.Executing,
            text = "Deactivating pod...",
            onNext = {}, onDiscard = {}, onCancel = {}
        )
    }
}

@Preview(showBackground = true, name = "Deactivate - Success")
@Composable
internal fun PreviewDeactivateSuccess() {
    MaterialTheme {
        DeactivatePodStepContent(
            actionState = ActionState.Idle, // Idle used as stand-in for Success in preview
            text = "Pod deactivated successfully.",
            onNext = {}, onDiscard = {}, onCancel = {}
        )
    }
}

@Preview(showBackground = true, name = "Deactivate - Error with Discard")
@Composable
internal fun PreviewDeactivateError() {
    MaterialTheme {
        DeactivatePodStepContent(
            actionState = ActionState.Error("Communication failed. Pod may need to be discarded."),
            text = "Deactivating pod...",
            onNext = {}, onDiscard = {}, onCancel = {}
        )
    }
}
