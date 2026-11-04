package app.aaps.plugins.automation.compose.elements

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true, widthDp = 360)
@Composable
internal fun PreviewDropdown() {
    MaterialTheme {
        var v by remember { mutableStateOf("Profile A") }
        AutomationDropdown(value = v, options = listOf("Profile A", "Profile B"), onValueChange = { v = it })
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
internal fun PreviewOnOff() {
    MaterialTheme {
        var v by remember { mutableStateOf(true) }
        InputDropdownOnOffEditor(on = v, onValueChange = { v = it })
    }
}
