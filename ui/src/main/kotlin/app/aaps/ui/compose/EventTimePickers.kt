package app.aaps.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.dialogs.DatePickerModal
import app.aaps.core.ui.compose.dialogs.TimePickerModal
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Date picker that merges the selected date with the existing time from [eventTimeMillis],
 * then calls [onEventTimeChanged] with the resulting epoch millis.
 *
 * Handles DST gaps: if the merged LocalDateTime falls in a DST gap,
 * [toInstant] adjusts automatically (kotlinx.datetime shifts to the valid offset).
 */
@Composable
fun EventDatePicker(
    eventTimeMillis: Long,
    onEventTimeChanged: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val tz = TimeZone.currentSystemDefault()
    DatePickerModal(
        onDateSelected = { selectedMillis ->
            selectedMillis?.let {
                val currentLdt = Instant.fromEpochMilliseconds(eventTimeMillis).toLocalDateTime(tz)
                val selectedDate = Instant.fromEpochMilliseconds(it).toLocalDateTime(tz).date
                val merged = LocalDateTime(selectedDate, currentLdt.time)
                onEventTimeChanged(merged.toInstant(tz).toEpochMilliseconds())
            }
        },
        onDismiss = onDismiss,
        initialDateMillis = eventTimeMillis
    )
}

/**
 * Time picker that merges the selected hour/minute with the existing date from [eventTimeMillis],
 * then calls [onEventTimeChanged] with the resulting epoch millis.
 *
 * Handles DST gaps: same as [EventDatePicker].
 */
@Composable
fun EventTimePicker(
    eventTimeMillis: Long,
    onEventTimeChanged: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val tz = TimeZone.currentSystemDefault()
    val currentLdt = Instant.fromEpochMilliseconds(eventTimeMillis).toLocalDateTime(tz)
    TimePickerModal(
        onTimeSelected = { hour, minute ->
            val merged = LocalDateTime(currentLdt.date, LocalTime(hour, minute))
            onEventTimeChanged(merged.toInstant(tz).toEpochMilliseconds())
        },
        onDismiss = onDismiss,
        initialHour = currentLdt.hour,
        initialMinute = currentLdt.minute,
        is24Hour = true
    )
}

@Preview(showBackground = true)
@Composable
private fun EventDatePickerPreview() {
    AapsTheme {
        EventDatePicker(
            eventTimeMillis = System.currentTimeMillis(),
            onEventTimeChanged = {},
            onDismiss = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EventTimePickerPreview() {
    AapsTheme {
        EventTimePicker(
            eventTimeMillis = System.currentTimeMillis(),
            onEventTimeChanged = {},
            onDismiss = {}
        )
    }
}
