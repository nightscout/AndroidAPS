package app.aaps.core.ui.compose.preference

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.jetbrains.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun PreferenceCategoryPreview() {
    PreviewTheme {
        PreferenceCategory(title = { Text("General") })
    }
}
