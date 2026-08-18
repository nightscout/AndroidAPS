package app.aaps.core.ui.compose.preference

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun TextFieldPreferencePreview() {
    PreviewTheme {
        TextFieldPreference(
            value = "Hello",
            onValueChange = {},
            title = { Text("Username") },
            textToValue = { it },
            summary = { Text("Hello") }
        )
    }
}
