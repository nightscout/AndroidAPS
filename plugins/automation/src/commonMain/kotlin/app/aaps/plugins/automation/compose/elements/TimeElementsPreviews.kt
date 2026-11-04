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
internal fun PreviewTime() {
    MaterialTheme {
        var m by remember { mutableStateOf(8 * 60 + 30) }
        InputTimeEditor(minutesSinceMidnight = m, onChange = { m = it })
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
internal fun PreviewTimeRange() {
    MaterialTheme {
        var s by remember { mutableStateOf(8 * 60) }
        var e by remember { mutableStateOf(20 * 60) }
        InputTimeRangeEditor(startMinutes = s, endMinutes = e, onChangeStart = { s = it }, onChangeEnd = { e = it })
    }
}
