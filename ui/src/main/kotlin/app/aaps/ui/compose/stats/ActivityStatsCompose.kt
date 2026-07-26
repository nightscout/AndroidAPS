package app.aaps.ui.compose.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.aaps.ui.R
import app.aaps.ui.activityMonitor.ActivityStats

/**
 * Composable that displays Activity Monitor statistics.
 * Shows a table with activity names, durations, and days since start.
 *
 * @param activityStats List of activity statistics
 * @param modifier Modifier for the root Column
 */
@Composable
fun ActivityStatsCompose(
    activityStats: List<ActivityStats>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Header row
        ActivityTableHeaderRow()

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // Data rows
        activityStats.forEach { stats ->
            ActivityTableDataRow(
                activityName = stats.activityName,
                duration = stats.duration,
                days = stringResource(app.aaps.core.interfaces.R.string.in_days, stats.days)
            )
        }
    }
}

/**
 * Composable that displays the header row of the Activity table.
 */
@Composable
private fun ActivityTableHeaderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(app.aaps.core.ui.R.string.activity),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Start
        )
        Text(
            text = stringResource(app.aaps.core.ui.R.string.duration),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.time_range),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
    }
}

/**
 * Composable that displays a single data row in the Activity table.
 */
@Composable
private fun ActivityTableDataRow(
    activityName: String,
    duration: String,
    days: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = activityName,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Start
        )
        Text(
            text = duration,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        Text(
            text = days,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
    }
}
