package app.aaps.core.ui.compose.preference

import androidx.compose.runtime.Composable
import org.jetbrains.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun AdaptiveMasterPasswordPreferencePreview() {
    PreviewTheme {
        AdaptiveMasterPasswordPreferenceItem(
            checkPassword = { _, _ -> false },
            hashPassword = { it },
            onShowMessage = { }
        )
    }
}
