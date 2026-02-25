package app.aaps.ui.compose.profileManagement

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
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
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.SliderWithButtons
import app.aaps.core.ui.compose.pickers.HourWheelPicker
import app.aaps.ui.compose.profileManagement.viewmodels.TimeValue
import java.text.DecimalFormat

@Composable
fun TimeValueList(
    title: String,
    entries: List<TimeValue>,
    onEntryChange: (Int, TimeValue) -> Unit,
    onAddEntry: (Int) -> Unit,
    onRemoveEntry: (Int) -> Unit,
    minValue: Double,
    maxValue: Double,
    step: Double = 0.1,
    valueFormat: DecimalFormat = DecimalFormat("0.0"),
    unitLabel: String = "",
    modifier: Modifier = Modifier
) {
    LocalDateUtil.current
    var showTimePicker by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableIntStateOf(-1) }

    Column(modifier = modifier.fillMaxWidth()) {
        if (title.isNotEmpty()) {
            Text(
                text = if (unitLabel.isNotEmpty()) "$title [$unitLabel]" else title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        entries.forEachIndexed { index, entry ->
            val currentHour = entry.timeSeconds / 3600
            val nextHour = if (index < entries.size - 1) entries[index + 1].timeSeconds / 3600 else 24
            // Can add if there's at least 2 hours gap (room for a new entry)
            val canAddAfter = entries.size < 24 && (nextHour - currentHour) >= 2

            TimeValueRow(
                index = index,
                timeSeconds = entry.timeSeconds,
                value = entry.value,
                onTimeClick = {
                    editingIndex = index
                    showTimePicker = true
                },
                onValueChange = { newValue ->
                    onEntryChange(index, entry.copy(value = newValue))
                },
                onRemove = { onRemoveEntry(index) },
                canRemove = entries.size > 1 && index > 0,
                canAdd = canAddAfter,
                onAdd = { onAddEntry(index) },
                minValue = minValue,
                maxValue = maxValue,
                step = step,
                valueFormat = valueFormat
            )
        }

        // Show time picker popup
        if (showTimePicker && editingIndex >= 0) {
            val currentEntry = entries.getOrNull(editingIndex)
            val currentHour = (currentEntry?.timeSeconds ?: 0) / 3600

            // Calculate available hours (between prev and next entry)
            val prevHour = if (editingIndex > 0) entries[editingIndex - 1].timeSeconds / 3600 else -1
            val nextHour = if (editingIndex < entries.size - 1) entries[editingIndex + 1].timeSeconds / 3600 else 24
            val availableHours = ((prevHour + 1) until nextHour).toList()

            if (availableHours.isNotEmpty()) {
                HourWheelPicker(
                    selectedHour = currentHour,
                    availableHours = availableHours,
                    onHourSelected = { hour ->
                        currentEntry?.let { entry ->
                            onEntryChange(editingIndex, entry.copy(timeSeconds = hour * 3600))
                        }
                    },
                    onDismiss = {
                        showTimePicker = false
                        editingIndex = -1
                    }
                )
            } else {
                showTimePicker = false
                editingIndex = -1
            }
        }

    }
}

@Composable
private fun TimeValueRow(
    index: Int,
    timeSeconds: Int,
    value: Double,
    onTimeClick: () -> Unit,
    onValueChange: (Double) -> Unit,
    onRemove: () -> Unit,
    canRemove: Boolean,
    canAdd: Boolean,
    onAdd: () -> Unit,
    minValue: Double,
    maxValue: Double,
    step: Double,
    valueFormat: DecimalFormat
) {
    val dateUtil = LocalDateUtil.current
    timeSeconds / 3600
    val timeString = dateUtil.timeStringFromSeconds(timeSeconds)

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Time chip
            SuggestionChip(
                onClick = { if (index > 0) onTimeClick() }, // First entry always at 00:00
                label = { Text(timeString) },
                modifier = Modifier.width(80.dp),
                enabled = index > 0,
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            )

            Spacer(Modifier.width(8.dp))

            // Value slider with +/- buttons and clickable value
            SliderWithButtons(
                value = value,
                onValueChange = onValueChange,
                valueRange = minValue..maxValue,
                step = step,
                showValue = true,
                valueFormat = valueFormat,
                modifier = Modifier.weight(1f)
            )

            // Add button (only shown if there's room for a new entry after this one)
            if (canAdd) {
                IconButton(
                    onClick = onAdd,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_time_block),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Spacer(Modifier.size(36.dp))
            }

            // Remove button
            IconButton(
                onClick = onRemove,
                enabled = canRemove,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.remove_entry),
                    modifier = Modifier.size(20.dp),
                    tint = if (canRemove) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }
    }
}

@Composable
fun TargetValueList(
    title: String,
    lowEntries: List<TimeValue>,
    highEntries: List<TimeValue>,
    onEntryChange: (Int, TimeValue, TimeValue) -> Unit,
    onAddEntry: (Int) -> Unit,
    onRemoveEntry: (Int) -> Unit,
    minValue: Double,
    maxValue: Double,
    step: Double = 1.0,
    valueFormat: DecimalFormat = DecimalFormat("0"),
    unitLabel: String = "",
    modifier: Modifier = Modifier
) {
    LocalDateUtil.current
    var showTimePicker by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableIntStateOf(-1) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = if (unitLabel.isNotEmpty()) "$title [$unitLabel]" else title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        lowEntries.zip(highEntries).forEachIndexed { index, (low, high) ->
            val currentHour = low.timeSeconds / 3600
            val nextHour = if (index < lowEntries.size - 1) lowEntries[index + 1].timeSeconds / 3600 else 24
            val canAddAfter = lowEntries.size < 24 && (nextHour - currentHour) >= 2

            TargetValueRow(
                index = index,
                timeSeconds = low.timeSeconds,
                lowValue = low.value,
                highValue = high.value,
                onTimeClick = {
                    editingIndex = index
                    showTimePicker = true
                },
                onLowValueChange = { newLow ->
                    val adjustedHigh = if (newLow > high.value) newLow else high.value
                    onEntryChange(index, low.copy(value = newLow), high.copy(value = adjustedHigh))
                },
                onHighValueChange = { newHigh ->
                    val adjustedLow = if (newHigh < low.value) newHigh else low.value
                    onEntryChange(index, low.copy(value = adjustedLow), high.copy(value = newHigh))
                },
                onRemove = { onRemoveEntry(index) },
                canRemove = lowEntries.size > 1 && index > 0,
                canAdd = canAddAfter,
                onAdd = { onAddEntry(index) },
                minValue = minValue,
                maxValue = maxValue,
                step = step,
                valueFormat = valueFormat
            )
        }

        // Show time picker popup
        if (showTimePicker && editingIndex >= 0) {
            val currentEntry = lowEntries.getOrNull(editingIndex)
            val currentHour = (currentEntry?.timeSeconds ?: 0) / 3600

            val prevHour = if (editingIndex > 0) lowEntries[editingIndex - 1].timeSeconds / 3600 else -1
            val nextHour = if (editingIndex < lowEntries.size - 1) lowEntries[editingIndex + 1].timeSeconds / 3600 else 24
            val availableHours = ((prevHour + 1) until nextHour).toList()

            if (availableHours.isNotEmpty()) {
                HourWheelPicker(
                    selectedHour = currentHour,
                    availableHours = availableHours,
                    onHourSelected = { hour ->
                        val low = lowEntries.getOrNull(editingIndex)
                        val high = highEntries.getOrNull(editingIndex)
                        if (low != null && high != null) {
                            onEntryChange(
                                editingIndex,
                                low.copy(timeSeconds = hour * 3600),
                                high.copy(timeSeconds = hour * 3600)
                            )
                        }
                    },
                    onDismiss = {
                        showTimePicker = false
                        editingIndex = -1
                    }
                )
            } else {
                showTimePicker = false
                editingIndex = -1
            }
        }

    }
}

@Composable
private fun TargetValueRow(
    index: Int,
    timeSeconds: Int,
    lowValue: Double,
    highValue: Double,
    onTimeClick: () -> Unit,
    onLowValueChange: (Double) -> Unit,
    onHighValueChange: (Double) -> Unit,
    onRemove: () -> Unit,
    canRemove: Boolean,
    canAdd: Boolean,
    onAdd: () -> Unit,
    minValue: Double,
    maxValue: Double,
    step: Double,
    valueFormat: DecimalFormat
) {
    val dateUtil = LocalDateUtil.current
    timeSeconds / 3600
    val timeString = dateUtil.timeStringFromSeconds(timeSeconds)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Time chip
            SuggestionChip(
                onClick = { if (index > 0) onTimeClick() },
                label = { Text(timeString) },
                modifier = Modifier.width(80.dp),
                enabled = index > 0,
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            )

            Spacer(Modifier.weight(1f))

            // Add button
            if (canAdd) {
                IconButton(
                    onClick = onAdd,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_time_block),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Spacer(Modifier.size(36.dp))
            }

            // Remove button
            IconButton(
                onClick = onRemove,
                enabled = canRemove,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.remove_entry),
                    modifier = Modifier.size(20.dp),
                    tint = if (canRemove) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }

        // Low value slider with +/- buttons and clickable value
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.target_low_label),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.width(40.dp)
            )

            SliderWithButtons(
                value = lowValue,
                onValueChange = onLowValueChange,
                valueRange = minValue..maxValue,
                step = step,
                showValue = true,
                valueFormat = valueFormat,
                dialogLabel = stringResource(R.string.target_low_label),
                modifier = Modifier.weight(1f)
            )
        }

        // High value slider with +/- buttons and clickable value
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.target_high_label),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.width(40.dp)
            )

            SliderWithButtons(
                value = highValue,
                onValueChange = onHighValueChange,
                valueRange = minValue..maxValue,
                step = step,
                showValue = true,
                valueFormat = valueFormat,
                dialogLabel = stringResource(R.string.target_high_label),
                modifier = Modifier.weight(1f)
            )
        }
    }
}
