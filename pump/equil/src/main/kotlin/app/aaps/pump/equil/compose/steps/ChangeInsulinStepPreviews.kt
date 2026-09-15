package app.aaps.pump.equil.compose.steps

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.pump.equil.R

@Preview(showBackground = true)
@Composable
internal fun ChangeInsulinStepPreview() {
    ChangeInsulinStepContent(
        isLoading = false,
        errorMessage = null,
        imageRes = R.drawable.equil_animation_wizard_detach,
        onNext = {},
        onCancel = {}
    )
}

@Preview(showBackground = true)
@Composable
internal fun ChangeInsulinStepLoadingPreview() {
    ChangeInsulinStepContent(
        isLoading = true,
        errorMessage = null,
        imageRes = R.drawable.equil_animation_wizard_detach,
        onNext = {},
        onCancel = {}
    )
}
