package app.aaps.ui.compose

import androidx.compose.runtime.Composable
import app.aaps.core.ui.compose.dialogs.DatePickerModal
import app.aaps.core.ui.compose.dialogs.TimePickerModal
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * How far the -/+ buttons of a careportal or fill entry can move its time, in minutes from now.
 *
 * A week back, for an entry that is logged late, and half a day ahead. The date and time pickers
 * are not limited by this. Shared, so a dialog and its view model cannot disagree.
 */
val EVENT_TIME_OFFSET_RANGE_MINUTES: IntRange = -7 * 24 * 60..12 * 60

/**
 * Date picker that merges the selected date with the existing time from [eventTimeMillis],
 * then calls [onEventTimeChanged] with the resulting epoch millis.
 *
 * Handles DST gaps: if the merged LocalDateTime falls in a DST gap,
 * [toInstant] adjusts automatically (kotlinx.datetime shifts to the valid offset).
 *
 * @see EventDatePickerPreview
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
 *
 * @see EventTimePickerPreview
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
