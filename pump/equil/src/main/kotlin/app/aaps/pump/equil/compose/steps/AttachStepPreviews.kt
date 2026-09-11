package app.aaps.pump.equil.compose.steps

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.pump.equil.R

@Preview(showBackground = true)
@Composable
internal fun AttachStepPreview() {
    AttachStepContent(
        imageRes = R.drawable.equil_animation_wizard_attach,
        onNext = {},
        onCancel = {}
    )
}
