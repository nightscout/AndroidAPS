package app.aaps.core.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.aaps.core.ui.CoreUiStrings

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
    val expandRequester = rememberBringIntoViewOnExpand(expanded)

    Column(modifier = modifier.fillMaxWidth().bringIntoViewRequester(expandRequester)) {
        ExpandableRowHeader(
            label = stringResource(CoreUiStrings.time),
            expanded = expanded,
            onExpand = { expanded = true }
        ) {
            Text(
                text = if (timeChanged) displayText else stringResource(CoreUiStrings.carb_time_now),
                style = MaterialTheme.typography.titleMedium,
                color = if (timeChanged) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            )
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
