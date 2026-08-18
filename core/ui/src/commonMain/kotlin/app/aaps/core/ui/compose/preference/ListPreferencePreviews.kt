package app.aaps.core.ui.compose.preference

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun ListPreferencePreview() {
    PreviewTheme {
        ListPreference(
            value = "Option B",
            onValueChange = {},
            values = listOf("Option A", "Option B", "Option C"),
            title = { Text("Choose option") },
            summary = { Text("Option B") }
        )
    }
}
