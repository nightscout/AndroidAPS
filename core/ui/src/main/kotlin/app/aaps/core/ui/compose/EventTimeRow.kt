package app.aaps.core.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
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

/**
 * Compact event time row with inline expand/collapse.
 *
 * Collapsed: `Time: Now                     [Change]`
 * Expanded:  `Time: 23/03 14:30`
 *              [Date field]  [Time field]
 *
 * No local state — date/time changes go directly to parent.
 *
 * @param timeChanged Whether the event time differs from "now"
 * @param displayText Formatted time string to show when changed (e.g., "23/03 14:30")
 * @param dateTimeContent Composable with date/time picker fields
 * @param modifier Modifier for the root column
 */
@Composable
fun EventTimeRow(
    timeChanged: Boolean,
    displayText: String,
    dateTimeContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
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
                    text = stringResource(R.string.time) + ": ",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (timeChanged) displayText else stringResource(R.string.carb_time_now),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (timeChanged) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
            }

            if (!expanded) {
                FilledTonalButton(onClick = { expanded = true }) {
                    Text(stringResource(R.string.change))
                }
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            dateTimeContent()
        }
    }
}
