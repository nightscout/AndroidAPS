package app.aaps.pump.equil.compose.steps

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun UnpairConfirmStepPreview() {
    UnpairConfirmStepContent(
        isLoading = false,
        errorMessage = null,
        unpairResult = null,
        serialNumber = "AB1234",
        onConfirm = {},
        onDismissResult = {},
        onCancel = {}
    )
}

@Preview(showBackground = true)
@Composable
internal fun UnpairConfirmStepLoadingPreview() {
    UnpairConfirmStepContent(
        isLoading = true,
        errorMessage = null,
        unpairResult = null,
        serialNumber = "AB1234",
        onConfirm = {},
        onDismissResult = {},
        onCancel = {}
    )
}

@Preview(showBackground = true)
@Composable
internal fun UnpairConfirmStepResultPreview() {
    UnpairConfirmStepContent(
        isLoading = false,
        errorMessage = null,
        unpairResult = "Device successfully unpaired",
        serialNumber = "",
        onConfirm = {},
        onDismissResult = {},
        onCancel = {}
    )
}

@Preview(showBackground = true)
@Composable
internal fun UnpairConfirmStepErrorPreview() {
    UnpairConfirmStepContent(
        isLoading = false,
        errorMessage = "Communication error",
        unpairResult = null,
        serialNumber = "AB1234",
        onConfirm = {},
        onDismissResult = {},
        onCancel = {}
    )
}
