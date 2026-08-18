package app.aaps.core.ui.compose.preference

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.UiStrings

@Preview(showBackground = true)
@Composable
internal fun CollapsibleCardSectionContentPreview() {
    PreviewTheme {
        CollapsibleCardSectionContent(
            title = UiStrings.configbuilder_insulin,
            expanded = true,
            onToggle = {}
        ) {
            Text("Section content", modifier = Modifier.padding(start = 16.dp))
        }
    }
}
