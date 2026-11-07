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
        newInfusionSet = null,
        imageRes = R.drawable.equil_animation_wizard_detach,
        onNewInfusionSet = {},
        onNext = {},
        onCancel = {}
    )
}

@Preview(showBackground = true)
@Composable
internal fun ChangeInsulinStepSameInfusionSetPreview() {
    ChangeInsulinStepContent(
        isLoading = false,
        errorMessage = null,
        newInfusionSet = false,
        imageRes = R.drawable.equil_animation_wizard_detach,
        onNewInfusionSet = {},
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
        newInfusionSet = true,
        imageRes = R.drawable.equil_animation_wizard_detach,
        onNewInfusionSet = {},
        onNext = {},
        onCancel = {}
    )
}
