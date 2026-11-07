package app.aaps.ui.compose.profileManagement

import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.aaps.core.data.format.NumberFormat
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.PlusMinusEdit
import app.aaps.core.ui.compose.pickers.HourWheelPicker
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings
import app.aaps.ui.compose.profileManagement.viewmodels.TimeValue

@Composable
fun TimeValueList(
    title: String,
    entries: List<TimeValue>,
    onEntryChange: (Int, TimeValue) -> Unit,
    onAddEntry: (Int) -> Unit,
    onRemoveEntry: (Int) -> Unit,
    valueRange: ClosedFloatingPointRange<Double>,
    step: Double = 0.1,
    valueFormat: NumberFormat = NumberFormat.DECIMAL_1,
    unitLabel: String = "",
    modifier: Modifier = Modifier
) {
    // Row whose time is being edited, -1 for none.
    var editingIndex by remember { mutableIntStateOf(-1) }

    val header = if (unitLabel.isNotEmpty()) "$title [$unitLabel]" else title
    Column(modifier = modifier.fillMaxWidth()) {
        if (title.isNotEmpty()) {
            Text(
                text = header,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        val hours = entries.map { it.timeSeconds / 3600 }
        entries.forEachIndexed { index, entry ->
            val nextHour = if (index < entries.lastIndex) hours[index + 1] else 24
            // Can add if there's at least 2 hours gap (room for a new entry)
            val canAddAfter = entries.size < 24 && (nextHour - hours[index]) >= 2

            TimeValueRow(
                index = index,
                timeSeconds = entry.timeSeconds,
                value = entry.value,
                onTimeClick = { editingIndex = index },
                timePicker = if (editingIndex == index) {
                    {
                        EntryHourPicker(
                            hours = hours,
                            index = index,
                            onHourSelected = { hour -> onEntryChange(index, entry.copy(timeSeconds = hour * 3600)) },
                            onDismiss = { editingIndex = -1 }
                        )
                    }
                } else null,
                onValueChange = { newValue ->
                    onEntryChange(index, entry.copy(value = newValue))
                },
                onRemove = { onRemoveEntry(index) },
                canRemove = entries.size > 1 && index > 0,
                canAdd = canAddAfter,
                onAdd = { onAddEntry(index) },
                valueRange = valueRange,
                step = step,
                valueFormat = valueFormat,
                header = header
            )
        }
    }
}

/**
 * The hour wheel for the entry at [index]. It offers the hours between the two neighbouring entries,
 * so the entries stay in time order.
 */
@Composable
private fun EntryHourPicker(
    hours: List<Int>,
    index: Int,
    onHourSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val prevHour = if (index > 0) hours[index - 1] else -1
    val nextHour = if (index < hours.lastIndex) hours[index + 1] else 24
    HourWheelPicker(
        selectedHour = hours[index],
        availableHours = ((prevHour + 1) until nextHour).toList(),
        onHourSelected = onHourSelected,
        onDismiss = onDismiss
    )
}

/**
 * The time of one row. The first row is always 00:00, so only later rows can open the picker.
 * [timePicker] is emitted inside the chip's [Box], which anchors the popup to the chip the user
 * tapped; emitted anywhere else it would open there instead.
 */
@Composable
private fun TimeChip(
    index: Int,
    timeString: String,
    onClick: () -> Unit,
    timePicker: (@Composable () -> Unit)?
) {
    Box {
        SuggestionChip(
            onClick = onClick,
            label = { Text(timeString) },
            modifier = Modifier.width(80.dp),
            enabled = index > 0,
            colors = SuggestionChipDefaults.suggestionChipColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        )
        timePicker?.invoke()
    }
}

@Composable
private fun TimeValueRow(
    index: Int,
    timeSeconds: Int,
    value: Double,
    onTimeClick: () -> Unit,
    timePicker: (@Composable () -> Unit)?,
    onValueChange: (Double) -> Unit,
    onRemove: () -> Unit,
    canRemove: Boolean,
    canAdd: Boolean,
    onAdd: () -> Unit,
    valueRange: ClosedFloatingPointRange<Double>,
    step: Double,
    valueFormat: NumberFormat,
    header: String
) {
    val dateUtil = LocalDateUtil.current
    val timeString = dateUtil.timeStringFromSeconds(timeSeconds)

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TimeChip(index = index, timeString = timeString, onClick = onTimeClick, timePicker = timePicker)

            Spacer(Modifier.width(8.dp))

            // Editable value with +/- stepper buttons
            PlusMinusEdit(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                step = step,
                valueFormat = valueFormat,
                // The time and the list are beside and above it on screen; a screen reader needs them
                // said, or every one of up to 24 rows is just "0.85, edit box".
                label = stringResource(UiStrings.a11y_profile_entry, header, timeString),
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
                        contentDescription = stringResource(CoreUiStrings.add_time_block),
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
                    contentDescription = stringResource(CoreUiStrings.remove_entry),
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
    lowRange: ClosedFloatingPointRange<Double>,
    highRange: ClosedFloatingPointRange<Double>,
    step: Double = 1.0,
    valueFormat: NumberFormat = NumberFormat.INTEGER,
    unitLabel: String = "",
    modifier: Modifier = Modifier
) {
    // Row whose time is being edited, -1 for none.
    var editingIndex by remember { mutableIntStateOf(-1) }

    val header = if (unitLabel.isNotEmpty()) "$title [$unitLabel]" else title
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = header,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        val hours = lowEntries.map { it.timeSeconds / 3600 }
        lowEntries.zip(highEntries).forEachIndexed { index, (low, high) ->
            val nextHour = if (index < lowEntries.lastIndex) hours[index + 1] else 24
            val canAddAfter = lowEntries.size < 24 && (nextHour - hours[index]) >= 2

            TargetValueRow(
                index = index,
                timeSeconds = low.timeSeconds,
                lowValue = low.value,
                highValue = high.value,
                onTimeClick = { editingIndex = index },
                timePicker = if (editingIndex == index) {
                    {
                        EntryHourPicker(
                            hours = hours,
                            index = index,
                            // Low and high share one time; move both.
                            onHourSelected = { hour -> onEntryChange(index, low.copy(timeSeconds = hour * 3600), high.copy(timeSeconds = hour * 3600)) },
                            onDismiss = { editingIndex = -1 }
                        )
                    }
                } else null,
                onLowValueChange = { newLow ->
                    // Keep low <= high: raising low above high pushes high up too, but only inside
                    // the range allowed for high.
                    val adjustedHigh = if (newLow > high.value) newLow.coerceIn(highRange) else high.value
                    onEntryChange(index, low.copy(value = newLow), high.copy(value = adjustedHigh))
                },
                onHighValueChange = { newHigh ->
                    // Keep low <= high: lowering high below low pulls low down too, but only inside
                    // the range allowed for low.
                    val adjustedLow = if (newHigh < low.value) newHigh.coerceIn(lowRange) else low.value
                    onEntryChange(index, low.copy(value = adjustedLow), high.copy(value = newHigh))
                },
                onRemove = { onRemoveEntry(index) },
                canRemove = lowEntries.size > 1 && index > 0,
                canAdd = canAddAfter,
                onAdd = { onAddEntry(index) },
                lowRange = lowRange,
                highRange = highRange,
                step = step,
                valueFormat = valueFormat,
                header = header
            )
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
    timePicker: (@Composable () -> Unit)?,
    onLowValueChange: (Double) -> Unit,
    onHighValueChange: (Double) -> Unit,
    onRemove: () -> Unit,
    canRemove: Boolean,
    canAdd: Boolean,
    onAdd: () -> Unit,
    lowRange: ClosedFloatingPointRange<Double>,
    highRange: ClosedFloatingPointRange<Double>,
    step: Double,
    valueFormat: NumberFormat,
    header: String
) {
    val dateUtil = LocalDateUtil.current
    val timeString = dateUtil.timeStringFromSeconds(timeSeconds)
    val lowLabel = stringResource(CoreUiStrings.target_low_label)
    val highLabel = stringResource(CoreUiStrings.target_high_label)
    // "Target [mmol/L]: Low at 08:00" - the list, the field and the time, for a screen reader.
    val lowSpoken = stringResource(UiStrings.a11y_profile_entry, stringResource(InterfacesStrings.confirmation_line, header, lowLabel), timeString)
    val highSpoken = stringResource(UiStrings.a11y_profile_entry, stringResource(InterfacesStrings.confirmation_line, header, highLabel), timeString)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TimeChip(index = index, timeString = timeString, onClick = onTimeClick, timePicker = timePicker)

            Spacer(Modifier.weight(1f))

            // Add button
            if (canAdd) {
                IconButton(
                    onClick = onAdd,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(CoreUiStrings.add_time_block),
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
                    contentDescription = stringResource(CoreUiStrings.remove_entry),
                    modifier = Modifier.size(20.dp),
                    tint = if (canRemove) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }

        // Low value editable stepper
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = lowLabel,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.width(40.dp)
            )

            PlusMinusEdit(
                value = lowValue,
                onValueChange = onLowValueChange,
                valueRange = lowRange,
                step = step,
                valueFormat = valueFormat,
                label = lowSpoken,
                modifier = Modifier.weight(1f)
            )
        }

        // High value editable stepper
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = highLabel,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.width(40.dp)
            )

            PlusMinusEdit(
                value = highValue,
                onValueChange = onHighValueChange,
                valueRange = highRange,
                step = step,
                valueFormat = valueFormat,
                label = highSpoken,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
