package app.aaps.plugins.automation.compose.elements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputTimeEditor(
    minutesSinceMidnight: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }
    var current by rememberSaveable(minutesSinceMidnight) { mutableIntStateOf(minutesSinceMidnight) }
    val hour = current / 60
    val minute = current % 60
    OutlinedButton(onClick = { showPicker = true }, modifier = modifier) {
        Text(formatHHmm(hour, minute))
    }
    if (showPicker) {
        val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
        TimePickerModal(
            state = state,
            onDismiss = { showPicker = false },
            onConfirm = {
                current = state.hour * 60 + state.minute
                onChange(current)
                showPicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputTimeRangeEditor(
    startMinutes: Int,
    endMinutes: Int,
    onChangeStart: (Int) -> Unit,
    onChangeEnd: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        InputTimeEditor(minutesSinceMidnight = startMinutes, onChange = onChangeStart)
        Text("–")
        InputTimeEditor(minutesSinceMidnight = endMinutes, onChange = onChangeEnd)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputDateTimeEditor(
    timeMillis: Long,
    onChange: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    val cal = remember(timeMillis) { Calendar.getInstance().apply { timeInMillis = timeMillis } }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(onClick = { showDate = true }) { Text(formatDate(cal)) }
        OutlinedButton(onClick = { showTime = true }) {
            Text(formatHHmm(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)))
        }
    }
    if (showDate) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = timeMillis)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { sel ->
                        val merged = Calendar.getInstance().apply { timeInMillis = sel }
                        cal.set(Calendar.YEAR, merged.get(Calendar.YEAR))
                        cal.set(Calendar.MONTH, merged.get(Calendar.MONTH))
                        cal.set(Calendar.DAY_OF_MONTH, merged.get(Calendar.DAY_OF_MONTH))
                        onChange(cal.timeInMillis)
                    }
                    showDate = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDate = false }) { Text(stringResource(android.R.string.cancel)) }
            }
        ) { DatePicker(state = dpState) }
    }
    if (showTime) {
        val tpState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            is24Hour = true
        )
        TimePickerModal(
            state = tpState,
            onDismiss = { showTime = false },
            onConfirm = {
                cal.set(Calendar.HOUR_OF_DAY, tpState.hour)
                cal.set(Calendar.MINUTE, tpState.minute)
                onChange(cal.timeInMillis)
                showTime = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerModal(
    state: TimePickerState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TimePicker(state = state)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
                    TextButton(onClick = onConfirm) { Text(stringResource(android.R.string.ok)) }
                }
            }
        }
    }
}

private fun formatHHmm(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

private fun formatDate(cal: Calendar): String =
    "%04d-%02d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun PreviewTime() {
    MaterialTheme {
        var m by remember { mutableStateOf(8 * 60 + 30) }
        InputTimeEditor(minutesSinceMidnight = m, onChange = { m = it })
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun PreviewTimeRange() {
    MaterialTheme {
        var s by remember { mutableStateOf(8 * 60) }
        var e by remember { mutableStateOf(20 * 60) }
        InputTimeRangeEditor(startMinutes = s, endMinutes = e, onChangeStart = { s = it }, onChangeEnd = { e = it })
    }
}
