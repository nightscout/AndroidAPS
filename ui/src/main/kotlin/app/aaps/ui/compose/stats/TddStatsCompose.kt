package app.aaps.ui.compose.stats

import androidx.collection.LongSparseArray
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
import app.aaps.core.data.aps.AverageTDD
import app.aaps.core.data.model.TDD
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.LocalDateUtil
import java.util.Locale

/**
 * Data class containing TDD (Total Daily Dose) statistics for profile calculations.
 * Used in Tab 1 of ProfileHelperActivity to display insulin usage statistics.
 *
 * @param tdds Historical TDD data indexed by date (LongSparseArray key is timestamp)
 * @param averageTdd Calculated average TDD over a period (weighted and exponential averages)
 * @param todayTdd Today's TDD data (may be incomplete if day is ongoing)
 */
data class TddStatsData(
    val tdds: LongSparseArray<TDD>?,
    val averageTdd: AverageTDD?,
    val todayTdd: TDD?
)

/**
 * Composable that displays Total Daily Dose (TDD) statistics in a table format.
 * Used in ProfileHelperActivity (Tab 1 and Tab 3) to show insulin usage history.
 *
 * The table displays:
 * - Historical TDD data: Date, Basal, Bolus, Total insulin, and Carbs for each day
 * - Average row: Calculated average TDD across the period (bold)
 * - Today row: Current day's TDD data (may be incomplete, bold)
 *
 * All values are formatted to 2 decimal places for insulin amounts and 0 decimals for carbs.
 *
 * **Table Structure:**
 * ```
 * Date       | Basal | Bolus | Total | Carbs
 * -----------|-------|-------|-------|-------
 * 2024-01-15 | 12.50 | 18.30 | 30.80 | 150
 * 2024-01-14 | 12.20 | 19.10 | 31.30 | 145
 * ...
 * -----------|-------|-------|-------|-------
 * Average    | 12.35 | 18.70 | 31.05 | 148
 * Today      | 8.50  | 12.40 | 20.90 | 95
 * ```
 *
 * @param tddStatsData TDD statistics data containing historical TDDs, average, and today's data
 * @param dateUtil Date formatting utility for displaying dates
 * @param modifier Modifier for the root Column
 */
@Composable
fun TddStatsCompose(
    tddStatsData: TddStatsData,
    modifier: Modifier = Modifier
) {
    val dateUtil = LocalDateUtil.current
    Column(modifier = modifier) {
        // Header row with column labels
        TddTableHeaderRow()

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // Historical TDD data rows (one per day)
        tddStatsData.tdds?.let { tdds ->
            for (i in 0 until tdds.size()) {
                val tdd = tdds.valueAt(i)
                TddTableDataRow(
                    date = dateUtil.dateString(tdd.timestamp),
                    basal = String.format(Locale.getDefault(), "%.2f", tdd.basalAmount),
                    bolus = String.format(Locale.getDefault(), "%.2f", tdd.bolusAmount),
                    total = String.format(Locale.getDefault(), "%.2f", tdd.totalAmount),
                    carbs = String.format(Locale.getDefault(), "%.0f", tdd.carbs)
                )
            }
        }

        // Average row
        tddStatsData.averageTdd?.let { avg ->
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Text(
                text = stringResource(R.string.average),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            TddTableDataRow(
                date = "",
                basal = String.format(Locale.getDefault(), "%.2f", avg.data.basalAmount),
                bolus = String.format(Locale.getDefault(), "%.2f", avg.data.bolusAmount),
                total = String.format(Locale.getDefault(), "%.2f", avg.data.totalAmount),
                carbs = String.format(Locale.getDefault(), "%.0f", avg.data.carbs),
                isBold = true
            )
        }

        // Today row
        tddStatsData.todayTdd?.let { today ->
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Text(
                text = stringResource(app.aaps.core.interfaces.R.string.today),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            TddTableDataRow(
                date = "",
                basal = String.format(Locale.getDefault(), "%.2f", today.basalAmount),
                bolus = String.format(Locale.getDefault(), "%.2f", today.bolusAmount),
                total = String.format(Locale.getDefault(), "%.2f", today.totalAmount),
                carbs = String.format(Locale.getDefault(), "%.0f", today.carbs),
                isBold = true
            )
        }
    }
}

/**
 * Composable that displays the header row of the TDD statistics table.
 * Shows column labels for Date, Basal rate, Bolus, Total, and Carbs.
 * All headers are bold and use labelMedium typography for visual hierarchy.
 * Uses weighted layout for consistent column alignment with data rows.
 */
@Composable
fun TddTableHeaderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(R.string.date),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1.5f),
            textAlign = TextAlign.Start
        )
        Text(
            text = stringResource(R.string.basalrate),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.bolus),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.tdd_total),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.carbs),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Composable that displays a single data row in the TDD statistics table.
 * Shows insulin and carb values for a specific day or summary row (average/today).
 *
 * @param date The date string to display (empty for average/today rows)
 * @param basal Basal insulin amount formatted as string (e.g., "12.50")
 * @param bolus Bolus insulin amount formatted as string (e.g., "18.30")
 * @param total Total daily insulin formatted as string (e.g., "30.80")
 * @param carbs Total carbohydrates formatted as string (e.g., "150")
 * @param isBold Whether to display text in bold (true for average and today rows)
 */
@Composable
fun TddTableDataRow(
    date: String,
    basal: String,
    bolus: String,
    total: String,
    carbs: String,
    isBold: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = date,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1.5f),
            textAlign = TextAlign.Start
        )
        Text(
            text = basal,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        Text(
            text = bolus,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        Text(
            text = total,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        Text(
            text = carbs,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
    }
}
