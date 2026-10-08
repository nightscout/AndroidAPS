package app.aaps.core.ui.compose.preference

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun PreferencePreview() {
    PreviewTheme {
        Preference(
            title = { Text("Preference title") },
            summary = { Text("Summary text") },
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            onClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun PreferenceDisabledPreview() {
    PreviewTheme {
        Preference(
            title = { Text("Disabled preference") },
            summary = { Text("This preference is disabled") },
            enabled = false
        )
    }
}
