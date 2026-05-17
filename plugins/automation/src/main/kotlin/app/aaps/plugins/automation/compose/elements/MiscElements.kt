package app.aaps.plugins.automation.compose.elements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.elements.WeekDay

@Composable
fun InputStringEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    singleLine: Boolean = true
) {
    // Local state to keep typing smooth; reconcile when external value actually changes.
    var text by remember { mutableStateOf(value) }
    androidx.compose.runtime.LaunchedEffect(value) {
        if (value != text) text = value
    }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onValueChange(it)
        },
        singleLine = singleLine,
        label = label?.let { { Text(it) } },
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * Mon–Sun selector using M3 FilterChips. [weekdays] is mutated in place;
 * [onChange] is invoked after each toggle to let callers refresh / persist.
 * Pass a [version] that you bump on each toggle to force recomposition since
 * [WeekDay] is not observable.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InputWeekDayEditor(
    weekdays: WeekDay,
    onChange: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Internal tick to re-render after mutation.
    var tick by remember { mutableIntStateOf(0) }
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        @Suppress("UNUSED_EXPRESSION") tick // read to subscribe
        WeekDay.DayOfWeek.entries.forEach { day ->
            val selected = weekdays.isSet(day)
            FilterChip(
                selected = selected,
                onClick = {
                    weekdays.set(day, !selected)
                    tick++
                    onChange()
                },
                label = { Text(stringResource(day.shortName)) }
            )
        }
    }
}

@Composable
fun InputButtonElement(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(onClick = onClick, modifier = modifier) { Text(text) }
}

@Composable
fun StaticLabelRow(
    label: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        actions()
    }
}

@Composable
fun LabelWithElementRow(
    textPre: String,
    textPost: String = "",
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (textPre.isNotEmpty()) {
            Text(textPre, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        Box(modifier = Modifier.weight(1f)) { content() }
        if (textPost.isNotEmpty()) {
            Text(textPost, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun AutomationElementColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) { content() }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun PreviewString() {
    MaterialTheme {
        var v by remember { mutableStateOf("Example") }
        InputStringEditor(value = v, onValueChange = { v = it }, label = "Text")
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun PreviewWeekdays() {
    MaterialTheme {
        val wd = remember { WeekDay().apply { set(WeekDay.DayOfWeek.MONDAY, true); set(WeekDay.DayOfWeek.WEDNESDAY, true) } }
        InputWeekDayEditor(weekdays = wd, onChange = {})
    }
}
