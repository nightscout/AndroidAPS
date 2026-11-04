package app.aaps.ui.compose.scenes.wizard

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true, showSystemUi = true)
@Composable
internal fun DurationStepPreview() {
    MaterialTheme {
        DurationStep(
            state = previewState,
            onSetDuration = {},
            onBack = {}, onNext = {}
        )
    }
}
