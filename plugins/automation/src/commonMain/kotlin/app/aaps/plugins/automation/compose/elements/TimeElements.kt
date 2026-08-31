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
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * @see PreviewTime
 */
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

/**
 * @see PreviewTimeRange
 */
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
    // Same reading as the Calendar this replaced: the default time zone, so what the user sees is
    // their own wall clock. Seconds and nanoseconds are carried through every edit, because setting
    // only the date or only the hour and minute must not quietly zero the rest.
    val zone = TimeZone.currentSystemDefault()
    val local = remember(timeMillis) { Instant.fromEpochMilliseconds(timeMillis).toLocalDateTime(zone) }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(onClick = { showDate = true }) { Text(formatDate(local)) }
        OutlinedButton(onClick = { showTime = true }) {
            Text(formatHHmm(local.hour, local.minute))
        }
    }
    if (showDate) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = timeMillis)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { sel ->
                        // Read back in the same zone the display uses, so the day the user tapped is the
                        // day that gets stored. Keeps the time of day untouched.
                        val pickedDate = Instant.fromEpochMilliseconds(sel).toLocalDateTime(zone).date
                        onChange(LocalDateTime(pickedDate, local.time).toInstant(zone).toEpochMilliseconds())
                    }
                    showDate = false
                }) { Text(stringResource(CoreUiStrings.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDate = false }) { Text(stringResource(CoreUiStrings.cancel)) }
            }
        ) { DatePicker(state = dpState) }
    }
    if (showTime) {
        val tpState = rememberTimePickerState(
            initialHour = local.hour,
            initialMinute = local.minute,
            is24Hour = true
        )
        TimePickerModal(
            state = tpState,
            onDismiss = { showTime = false },
            onConfirm = {
                val pickedTime = LocalTime(tpState.hour, tpState.minute, local.second, local.nanosecond)
                onChange(LocalDateTime(local.date, pickedTime).toInstant(zone).toEpochMilliseconds())
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
                    TextButton(onClick = onDismiss) { Text(stringResource(CoreUiStrings.cancel)) }
                    TextButton(onClick = onConfirm) { Text(stringResource(CoreUiStrings.ok)) }
                }
            }
        }
    }
}

private fun formatHHmm(hour: Int, minute: Int): String =
    "${pad(hour, 2)}:${pad(minute, 2)}"

// LocalDate already prints ISO yyyy-MM-dd, which is what the old "%04d-%02d-%02d" built by hand.
private fun formatDate(dateTime: LocalDateTime): String = dateTime.date.toString()

// String.format is JVM only, so pad by hand. Same output as the "%02d" it replaces.
private fun pad(value: Int, length: Int): String = value.toString().padStart(length, '0')
