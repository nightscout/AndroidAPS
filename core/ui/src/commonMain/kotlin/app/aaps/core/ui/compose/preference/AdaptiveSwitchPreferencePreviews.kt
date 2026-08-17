package app.aaps.core.ui.compose.preference

import androidx.compose.runtime.Composable
import org.jetbrains.compose.ui.tooling.preview.Preview
import app.aaps.core.keys.BooleanKey

@Preview(showBackground = true)
@Composable
internal fun AdaptiveSwitchPreferencePreview() {
    PreviewTheme {
        AdaptiveSwitchPreferenceItem(
            booleanKey = BooleanKey.OverviewKeepScreenOn
        )
    }
}
