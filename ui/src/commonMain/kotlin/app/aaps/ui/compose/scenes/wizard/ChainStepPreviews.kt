package app.aaps.ui.compose.scenes.wizard

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.data.model.Scene

@Preview(showBackground = true, showSystemUi = true)
@Composable
internal fun ChainStepPreview() {
    MaterialTheme {
        ChainStep(
            state = previewState,
            availableTargets = listOf(
                Scene(id = "b", name = "Post-Meal"),
                Scene(id = "c", name = "Recovery")
            ),
            onSetChainTarget = {},
            onBack = {}, onNext = {}
        )
    }
}
