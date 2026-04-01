package app.aaps.core.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.dialogs.TimePickerModal

/**
 * Time range picker for selecting a start and end time (e.g., valid hours for QuickWizard).
 * Displays the selected range and opens dialogs to edit start/end times.
 *
 * @param label Label text displayed above the picker
 * @param startSeconds Start time in seconds from midnight
 * @param endSeconds End time in seconds from midnight
 * @param onStartChange Callback when start time changes
 * @param onEndChange Callback when end time changes
 * @param modifier Modifier for the component
 */
@Composable
fun TimeRangePicker(
    label: String,
    startSeconds: Int,
    endSeconds: Int,
    onStartChange: (Int) -> Unit,
    onEndChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val dateUtil = LocalDateUtil.current
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    // Convert seconds to time strings
    val startTimeString = dateUtil.timeString(dateUtil.secondsOfTheDayToMillisecondsOfHoursAndMinutes(startSeconds))
    val endTimeString = dateUtil.timeString(dateUtil.secondsOfTheDayToMillisecondsOfHoursAndMinutes(endSeconds))

    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Start time
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { showStartPicker = true }
                ) {
                    Text(
                        text = stringResource(R.string.from_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = startTimeString,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Separator
                Text(
                    text = "—",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // End time
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { showEndPicker = true }
                ) {
                    Text(
                        text = stringResource(R.string.to_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = endTimeString,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    // Start time picker dialog
    if (showStartPicker) {
        val currentHour = startSeconds / 3600
        val currentMinute = (startSeconds % 3600) / 60

        TimePickerModal(
            onTimeSelected = { hour, minute ->
                val newSeconds = hour * 3600 + minute * 60
                onStartChange(newSeconds)
            },
            onDismiss = { showStartPicker = false },
            initialHour = currentHour,
            initialMinute = currentMinute,
            is24Hour = true
        )
    }

    // End time picker dialog
    if (showEndPicker) {
        val currentHour = endSeconds / 3600
        val currentMinute = (endSeconds % 3600) / 60

        TimePickerModal(
            onTimeSelected = { hour, minute ->
                val newSeconds = hour * 3600 + minute * 60
                onEndChange(newSeconds)
            },
            onDismiss = { showEndPicker = false },
            initialHour = currentHour,
            initialMinute = currentMinute,
            is24Hour = true
        )
    }
}
