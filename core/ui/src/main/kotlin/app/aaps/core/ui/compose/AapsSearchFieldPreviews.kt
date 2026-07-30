package app.aaps.core.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun AapsSearchFieldPreview() {
    MaterialTheme {
        AapsSearchField(
            query = "",
            onQueryChange = {}
        )
    }
}
