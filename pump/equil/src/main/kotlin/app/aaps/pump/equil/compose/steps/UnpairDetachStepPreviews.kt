package app.aaps.pump.equil.compose.steps

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.pump.equil.R

@Preview(showBackground = true)
@Composable
internal fun UnpairDetachStepPreview() {
    UnpairDetachStepContent(
        isLoading = false,
        errorMessage = null,
        imageRes = R.drawable.equil_animation_wizard_detach,
        onNext = {},
        onCancel = {}
    )
}

@Preview(showBackground = true)
@Composable
internal fun UnpairDetachStepLoadingPreview() {
    UnpairDetachStepContent(
        isLoading = true,
        errorMessage = null,
        imageRes = R.drawable.equil_animation_wizard_detach,
        onNext = {},
        onCancel = {}
    )
}

@Preview(showBackground = true)
@Composable
internal fun UnpairDetachStepErrorPreview() {
    UnpairDetachStepContent(
        isLoading = false,
        errorMessage = "Communication error",
        imageRes = R.drawable.equil_animation_wizard_detach,
        onNext = {},
        onCancel = {}
    )
}
