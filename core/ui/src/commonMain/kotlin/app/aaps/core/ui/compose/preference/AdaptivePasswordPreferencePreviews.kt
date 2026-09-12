package app.aaps.core.ui.compose.preference

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.keys.StringKey

@Preview(showBackground = true)
@Composable
internal fun AdaptivePasswordPreferencePreview() {
    PreviewTheme {
        AdaptivePasswordPreferenceItem(
            stringKey = StringKey.ProtectionSettingsPassword,
            hashPassword = { it },
            onShowMessage = { }
        )
    }
}
