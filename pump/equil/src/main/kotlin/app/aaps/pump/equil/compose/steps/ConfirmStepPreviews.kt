package app.aaps.pump.equil.compose.steps

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun ConfirmStepPreview() {
    ConfirmStepContent(
        isLoading = false,
        errorMessage = null,
        onFinish = {},
        onBack = null,
        onCancel = {}
    )
}

@Preview(showBackground = true)
@Composable
internal fun ConfirmStepLoadingPreview() {
    ConfirmStepContent(
        isLoading = true,
        errorMessage = null,
        onFinish = {},
        onBack = null,
        onCancel = {}
    )
}

@Preview(showBackground = true)
@Composable
internal fun ConfirmStepErrorPreview() {
    ConfirmStepContent(
        isLoading = false,
        errorMessage = "Communication error",
        onFinish = {},
        onBack = null,
        onCancel = {}
    )
}
