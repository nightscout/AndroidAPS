package app.aaps.core.ui.compose.preference

import androidx.compose.runtime.Composable
import app.aaps.core.keys.DoubleKey
import org.jetbrains.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun AdaptiveDoublePreferencePreview() {
    PreviewTheme {
        AdaptiveDoublePreferenceItem(
            doubleKey = DoubleKey.OverviewInsulinButtonIncrement1
        )
    }
}
