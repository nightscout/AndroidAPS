package app.aaps.pump.equil.compose.steps

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun FillStepIdlePreview() {
    FillStepContent(
        isLoading = false,
        autoFilling = false,
        fillComplete = false,
        errorMessage = null,
        onStartFill = {},
        onStopFill = {},
        onNext = {},
        onCancel = {}
    )
}

@Preview(showBackground = true)
@Composable
internal fun FillStepFillingPreview() {
    FillStepContent(
        isLoading = false,
        autoFilling = true,
        fillComplete = false,
        errorMessage = null,
        onStartFill = {},
        onStopFill = {},
        onNext = {},
        onCancel = {}
    )
}

@Preview(showBackground = true)
@Composable
internal fun FillStepCompletePreview() {
    FillStepContent(
        isLoading = false,
        autoFilling = false,
        fillComplete = true,
        errorMessage = null,
        onStartFill = {},
        onStopFill = {},
        onNext = {},
        onCancel = {}
    )
}

@Preview(showBackground = true)
@Composable
internal fun FillStepErrorPreview() {
    FillStepContent(
        isLoading = false,
        autoFilling = false,
        fillComplete = false,
        errorMessage = "Replace reservoir",
        onStartFill = {},
        onStopFill = {},
        onNext = {},
        onCancel = {}
    )
}
