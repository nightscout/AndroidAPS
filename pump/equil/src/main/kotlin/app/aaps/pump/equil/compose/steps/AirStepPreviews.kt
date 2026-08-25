package app.aaps.pump.equil.compose.steps

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun AirStepPreview() {
    AirStepContent(
        isLoading = false,
        errorMessage = null,
        airRemovalDone = false,
        onRemoveAir = {},
        onFinish = {},
        onCancel = {}
    )
}

@Preview(showBackground = true)
@Composable
internal fun AirStepDonePreview() {
    AirStepContent(
        isLoading = false,
        errorMessage = null,
        airRemovalDone = true,
        onRemoveAir = {},
        onFinish = {},
        onCancel = {}
    )
}

@Preview(showBackground = true)
@Composable
internal fun AirStepErrorPreview() {
    AirStepContent(
        isLoading = false,
        errorMessage = "Communication error",
        airRemovalDone = false,
        onRemoveAir = {},
        onFinish = {},
        onCancel = {}
    )
}
