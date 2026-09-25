package app.aaps.core.graph.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun ProfileRowPreview() {
    MaterialTheme {
        Column {
            ProfileRow(label = "Units", value = "mg/dL")
            ProfileRow(label = "IC", value = "08:00 10.0\n12:00 8.5\n18:00 9.0")
            ProfileInlineRow(label = "Insulin", value = "Humalog")
        }
    }
}
