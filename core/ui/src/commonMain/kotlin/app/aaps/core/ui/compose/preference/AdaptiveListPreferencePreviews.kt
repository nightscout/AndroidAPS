package app.aaps.core.ui.compose.preference

import androidx.compose.runtime.Composable
import app.aaps.core.keys.IntKey
import org.jetbrains.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun AdaptiveListIntPreferencePreview() {
    PreviewTheme {
        AdaptiveListIntPreferenceItem(
            intKey = IntKey.OverviewCarbsButtonIncrement1,
            entries = listOf("5g", "10g", "15g", "20g"),
            entryValues = listOf(5, 10, 15, 20)
        )
    }
}
