package app.aaps.core.ui.compose.preference

import androidx.compose.runtime.Composable
import app.aaps.core.keys.StringKey
import org.jetbrains.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun AdaptiveStringPreferencePreview() {
    PreviewTheme {
        AdaptiveStringPreferenceItem(
            stringKey = StringKey.GeneralPatientName
        )
    }
}
