package app.aaps.ui.compose.scenes.wizard

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true, showSystemUi = true)
@Composable
internal fun TempTargetStepPreview() {
    MaterialTheme {
        TempTargetStep(
            state = previewState,
            onToggle = {}, onUpdate = {},
            ttPresets = previewPresets,
            formatBgWithUnits = { "${it.toInt()} mg/dl" },
            onBack = {}, onNext = {}
        )
    }
}
