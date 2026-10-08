package app.aaps.plugins.automation.compose.elements

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.ui.elements.WeekDay

@Preview(showBackground = true, widthDp = 360)
@Composable
internal fun PreviewString() {
    MaterialTheme {
        var v by remember { mutableStateOf("Example") }
        InputStringEditor(value = v, onValueChange = { v = it }, label = "Text")
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
internal fun PreviewWeekdays() {
    MaterialTheme {
        val wd = remember { WeekDay().apply { set(WeekDay.DayOfWeek.MONDAY, true); set(WeekDay.DayOfWeek.WEDNESDAY, true) } }
        InputWeekDayEditor(weekdays = wd, onChange = {})
    }
}
