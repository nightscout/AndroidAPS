package app.aaps.core.ui.compose.pickers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.elements.WeekDay

/**
 * Compose weekday selector using FilterChips.
 *
 * @param selectedDays Boolean array of size 7 (Mon..Sun) matching [WeekDay.DayOfWeek] ordinals.
 * @param onDayToggle Called with the [WeekDay.DayOfWeek] and new selected state.
 * @param enabled Whether the chips are interactive.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeekDaySelector(
    selectedDays: BooleanArray,
    onDayToggle: (WeekDay.DayOfWeek, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        WeekDay.DayOfWeek.entries.forEach { day ->
            FilterChip(
                selected = selectedDays.getOrElse(day.ordinal) { false },
                onClick = {
                    if (enabled) onDayToggle(day, !selectedDays.getOrElse(day.ordinal) { false })
                },
                enabled = enabled,
                label = {
                    Text(
                        text = stringResource(day.shortName),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WeekDaySelectorPreview() {
    MaterialTheme {
        WeekDaySelector(
            selectedDays = booleanArrayOf(true, false, true, false, true, false, true),
            onDayToggle = { _, _ -> }
        )
    }
}
