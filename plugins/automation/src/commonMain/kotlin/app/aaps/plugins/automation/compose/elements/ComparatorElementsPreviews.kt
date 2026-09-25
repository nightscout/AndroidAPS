package app.aaps.plugins.automation.compose.elements

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.plugins.automation.elements.Comparator

@Preview(showBackground = true, widthDp = 420)
@Composable
internal fun PreviewComparator() {
    MaterialTheme {
        var v by remember { mutableStateOf(Comparator.Compare.IS_EQUAL) }
        ComparatorEditor(value = v, onValueChange = { v = it })
    }
}
