package app.aaps.core.ui.compose.preference

import androidx.compose.runtime.Composable
import org.jetbrains.compose.ui.tooling.preview.Preview
import app.aaps.core.keys.IntKey

@Preview(showBackground = true)
@Composable
internal fun AdaptiveIntPreferencePreview() {
    PreviewTheme {
        AdaptiveIntPreferenceItem(
            intKey = IntKey.OverviewCarbsButtonIncrement1
        )
    }
}
