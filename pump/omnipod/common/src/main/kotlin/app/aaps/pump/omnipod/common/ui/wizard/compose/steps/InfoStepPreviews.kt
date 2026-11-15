package app.aaps.pump.omnipod.common.ui.wizard.compose.steps

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true, name = "Info - With Cancel")
@Composable
internal fun PreviewInfoStep() {
    MaterialTheme {
        InfoStepContent(
            text = "Please follow the instructions to prepare your new pod for activation.",
            isFinishStep = false,
            onNext = {},
            onCancel = {}
        )
    }
}

@Preview(showBackground = true, name = "Info - Finish")
@Composable
internal fun PreviewInfoStepFinish() {
    MaterialTheme {
        InfoStepContent(
            text = "Your pod has been successfully activated!",
            isFinishStep = true,
            onNext = {},
            onCancel = null
        )
    }
}
