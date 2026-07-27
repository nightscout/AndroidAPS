package app.aaps.pump.medtrum.compose.steps

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true, name = "Attach Step")
@Composable
internal fun PreviewAttachStep() {
    MaterialTheme {
        AttachStepContent(onNext = {}, onCancel = {})
    }
}
