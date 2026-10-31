package app.aaps.pump.omnipod.common.ui.wizard.compose.steps

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true, name = "Attach Pod")
@Composable
internal fun PreviewAttachPod() {
    MaterialTheme {
        AttachPodStepContent(
            text = "Fill the new pod with insulin. You will hear two beeps when the pod is ready.",
            onNext = {},
            onCancel = {}
        )
    }
}
