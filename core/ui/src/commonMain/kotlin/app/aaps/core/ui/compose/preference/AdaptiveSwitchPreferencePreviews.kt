package app.aaps.core.ui.compose.preference

import androidx.compose.runtime.Composable
import app.aaps.core.keys.BooleanKey
import org.jetbrains.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun AdaptiveSwitchPreferencePreview() {
    PreviewTheme {
        AdaptiveSwitchPreferenceItem(
            booleanKey = BooleanKey.OverviewKeepScreenOn
        )
    }
}
