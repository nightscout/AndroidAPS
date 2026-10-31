package app.aaps.pump.equil.compose.steps

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.pump.equil.R

@Preview(showBackground = true)
@Composable
internal fun AssembleStepPairPreview() {
    AssembleStepContent(
        isDressing = false,
        imageRes = R.drawable.equil_animation_wizard_assemble,
        onNext = {},
        onCancel = {}
    )
}

@Preview(showBackground = true)
@Composable
internal fun AssembleStepDressingPreview() {
    AssembleStepContent(
        isDressing = true,
        imageRes = R.drawable.equil_animation_wizard_assemble,
        onNext = {},
        onCancel = {}
    )
}
