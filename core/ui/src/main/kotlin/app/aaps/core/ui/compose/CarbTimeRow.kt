package app.aaps.core.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.R
import app.aaps.core.keys.R as KeysR

/**
 * Compact carb time row with inline expand/collapse.
 *
 * Collapsed: `🕐 Carb time: Now                    [Change]`
 * Expanded:  `🕐 Carb time: +15 min 🔔                [OK]`
 *              NumberInputRow for offset
 *              Alarm toggle
 *              Optional date/time pickers (Carbs dialog)
 *
 * No local state — all changes go directly to parent callbacks.
 *
 * @param offsetMinutes Current time offset in minutes (0 = now)
 * @param alarmChecked Whether the carb reminder alarm is enabled
 * @param onOffsetChange Called immediately when offset changes (no local buffering)
 * @param onAlarmChange Called immediately when alarm toggle changes
 * @param resolvedTimeText Formatted time string (e.g., "17:55") when offset != 0
 * @param offsetRange Allowed range for the offset
 * @param offsetStep Step for the +/- buttons
 * @param dateTimeContent Optional composable for date/time pickers (Carbs dialog only)
 * @param modifier Modifier for the root column
 */
@Composable
fun CarbTimeRow(
    offsetMinutes: Int,
    alarmChecked: Boolean,
    onOffsetChange: (Int) -> Unit,
    onAlarmChange: (Boolean) -> Unit,
    resolvedTimeText: String? = null,
    offsetRange: IntRange = -60..60,
    offsetStep: Int = 5,
    dateTimeContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Header row: icon + label + value + Change/OK button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.wizard_carb_time) + ": ",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (offsetMinutes == 0) {
                    Text(
                        text = stringResource(R.string.carb_time_now),
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
                            text = "(${formatOffset(offsetMinutes)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (alarmChecked && offsetMinutes != 0) {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (!expanded) {
                FilledTonalButton(onClick = { expanded = true }) {
                    Text(stringResource(R.string.change))
                }
            }
        }

        // Expandable content — no local state, all changes go directly to parent
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(8.dp))

                // Offset input
                NumberInputRow(
                    labelResId = R.string.time,
                    value = offsetMinutes.toDouble(),
                    onValueChange = { onOffsetChange(it.toInt()) },
                    valueRange = offsetRange.first.toDouble()..offsetRange.last.toDouble(),
                    step = offsetStep.toDouble(),
                    unitLabelResId = KeysR.string.units_min
                )

                // Alarm toggle (disabled when offset <= 0)
                val alarmEnabled = offsetMinutes > 0
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = alarmEnabled) { onAlarmChange(!alarmChecked) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val alarmAlpha = if (alarmEnabled) 1f else 0.38f
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alarmAlpha),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.wizard_set_alarm),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alarmAlpha)
                        )
                    }
                    Switch(
                        checked = alarmChecked && alarmEnabled,
                        onCheckedChange = { onAlarmChange(it) },
                        enabled = alarmEnabled
                    )
                }

                // Optional date/time content (Carbs dialog)
                if (dateTimeContent != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    dateTimeContent()
                }
            }
        }
    }
}

@Composable
private fun formatOffset(minutes: Int): String {
    return when {
        minutes == 0 -> stringResource(R.string.carb_time_now)
        minutes > 0  -> "+${formatMinutesAsDuration(minutes)}"
        else         -> "-${formatMinutesAsDuration(-minutes)}"
    }
}
