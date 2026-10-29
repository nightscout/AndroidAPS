package app.aaps.core.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings

/**
 * When something happened, as minutes from now, in a row that is usually left at "Now".
 *
 * Collapsed: `Carb time: Now                 (-) (+) (chevron)`
 *            `Carb time: 17:55 (+15 min) 🔔   (-) (+) (chevron)`
 * Expanded:  the minutes field, then the alarm switch and the date/time pickers when the caller
 *            gives them.
 *
 * No local state — all changes go directly to parent callbacks.
 *
 * @param labelRef the row name, such as "Carb time" or "Time". The field inside is always "Time".
 * @param offsetMinutes Current time offset in minutes (0 = now)
 * @param onOffsetChange Called immediately when offset changes (no local buffering)
 * @param alarmChecked Whether the carb reminder alarm is enabled
 * @param onAlarmChange Called immediately when alarm toggle changes. Null means the row has no alarm.
 * @param resolvedTimeText Formatted time string (e.g., "17:55") when offset != 0
 * @param offsetRange Allowed range for the offset
 * @param offsetStep Step for the +/- buttons
 * @param dateTimeContent Optional composable for date/time pickers
 * @param modifier Modifier for the root column
 */
@Composable
fun TimeOffsetRow(
    labelRef: TextRef,
    offsetMinutes: Int,
    onOffsetChange: (Int) -> Unit,
    alarmChecked: Boolean = false,
    onAlarmChange: ((Boolean) -> Unit)? = null,
    resolvedTimeText: String? = null,
    offsetRange: IntRange = -60..60,
    offsetStep: Int = 5,
    dateTimeContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val expandRequester = rememberBringIntoViewOnExpand(expanded)

    ExpandableNumberInputRow(
        labelRef = labelRef,
        // The row says what the time is for, the field inside it only "Time".
        inputLabelRef = CoreUiStrings.time,
        value = offsetMinutes.toDouble(),
        onValueChange = { onOffsetChange(it.toInt()) },
        valueRange = offsetRange.first.toDouble()..offsetRange.last.toDouble(),
        step = offsetStep.toDouble(),
        unitLabel = CoreUiStrings.units_min,
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(expandRequester),
        valueContent = {
            if (offsetMinutes == 0) {
                Text(
                    text = stringResource(CoreUiStrings.carb_time_now),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                Column {
                    if (resolvedTimeText != null) {
                        Text(
                            text = resolvedTimeText,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = stringResource(CoreUiStrings.value_in_parentheses, formatOffset(offsetMinutes)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            // As the switch below: the alarm is set only for a time in the future. The flag stays on when the
            // time goes to now or earlier, so it is not enough on its own (#5415).
            if (onAlarmChange != null && alarmChecked && offsetMinutes > 0) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    // The only sign that an alarm is set when the row is collapsed.
                    contentDescription = stringResource(CoreUiStrings.wizard_set_alarm),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        },
        // No local state here either - all changes go straight to the parent.
        expandedContent = {
            if (onAlarmChange != null) {
                // Alarm toggle (disabled when offset <= 0)
                val alarmEnabled = offsetMinutes > 0
                Spacer(modifier = Modifier.height(4.dp))
                val alarmAlpha = if (alarmEnabled) 1f else 0.38f
                LabeledSwitch(
                    label = stringResource(CoreUiStrings.wizard_set_alarm),
                    checked = alarmChecked && alarmEnabled,
                    onCheckedChange = onAlarmChange,
                    enabled = alarmEnabled,
                    labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alarmAlpha),
                    labelStyle = MaterialTheme.typography.bodyMedium,
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alarmAlpha),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(20.dp)
                        )
                    }
                )
            }

            if (dateTimeContent != null) {
                Spacer(modifier = Modifier.height(8.dp))
                dateTimeContent()
            }
        }
    )
}

@Composable
private fun formatOffset(minutes: Int): String {
    return when {
        minutes == 0 -> stringResource(CoreUiStrings.carb_time_now)
        minutes > 0  -> stringResource(CoreUiStrings.value_with_plus_sign, formatMinutesAsDuration(minutes))
        else         -> stringResource(CoreUiStrings.value_with_minus_sign, formatMinutesAsDuration(-minutes))
    }
}
