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
import app.aaps.core.interfaces.stats.TIR
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.LocalProfileUtil
import java.util.Locale

/**
 * Data class containing TIR (Time In Range) statistics.
 * Used in StatsActivity to display time in range statistics.
 *
 * @param tir7 TIR data for last 7 days
 * @param averageTir7 Average TIR for 7 days
 * @param averageTir30 Average TIR for 30 days
 * @param lowTirMgdl Low threshold in mg/dL
 * @param highTirMgdl High threshold in mg/dL
 * @param lowTitMgdl Low threshold for Time In Target in mg/dL
 * @param highTitMgdl High threshold for Time In Target in mg/dL
 * @param averageTit7 Average Time In Target for 7 days
 * @param averageTit30 Average Time In Target for 30 days
 */
data class TirStatsData(
    val tir7: LongSparseArray<TIR>?,
    val averageTir7: TIR?,
    val averageTir30: TIR?,
    val lowTirMgdl: Double,
    val highTirMgdl: Double,
    val lowTitMgdl: Double,
    val highTitMgdl: Double,
    val averageTit7: TIR?,
    val averageTit30: TIR?
)

/**
 * Composable that displays Time In Range (TIR) statistics in a table format.
 * Used in StatsActivity to show TIR data.
 *
 * The table displays:
 * - Historical TIR data: Date, Below, In Range, Above percentages for each day
 * - Average rows: TIR and TIT averages for 7 and 30 days
 *
 * @param tirStatsData TIR statistics data
 * @param modifier Modifier for the root Column
 */
@Composable
fun TirStatsCompose(
    tirStatsData: TirStatsData,
    modifier: Modifier = Modifier
) {
    val profileUtil = LocalProfileUtil.current
    val dateUtil = LocalDateUtil.current
    Column(modifier = modifier) {
        // TIR Section
        Text(
            text = stringResource(R.string.tir) + " (" +
                profileUtil.stringInCurrentUnitsDetect(tirStatsData.lowTirMgdl) + "-" +
                profileUtil.stringInCurrentUnitsDetect(tirStatsData.highTirMgdl) + ")",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )

        // Header row
        TirTableHeaderRow()

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // Historical TIR data rows (last 7 days)
        tirStatsData.tir7?.let { tir7 ->
            for (i in 0 until tir7.size()) {
                val tir = tir7.valueAt(i)
                TirTableDataRow(
                    date = dateUtil.dateString(tir.date),
                    below = formatPercentage(tir.below, tir.count),
                    inRange = formatPercentage(tir.inRange, tir.count),
                    above = formatPercentage(tir.above, tir.count)
                )
            }
        }

        // Average TIR section
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Text(
            text = stringResource(R.string.average) + " (" +
                profileUtil.stringInCurrentUnitsDetect(tirStatsData.lowTirMgdl) + "-" +
                profileUtil.stringInCurrentUnitsDetect(tirStatsData.highTirMgdl) + ")",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        tirStatsData.averageTir7?.let { avg ->
            TirTableDataRow(
                date = "7d",
                below = formatPercentage(avg.below, avg.count),
                inRange = formatPercentage(avg.inRange, avg.count),
                above = formatPercentage(avg.above, avg.count),
                isBold = true
            )
        }

        tirStatsData.averageTir30?.let { avg ->
            TirTableDataRow(
                date = "30d",
                below = formatPercentage(avg.below, avg.count),
                inRange = formatPercentage(avg.inRange, avg.count),
                above = formatPercentage(avg.above, avg.count),
                isBold = true
            )
        }

        // Average TIT section
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Text(
            text = stringResource(R.string.average) + " (" +
                profileUtil.stringInCurrentUnitsDetect(tirStatsData.lowTitMgdl) + "-" +
                profileUtil.stringInCurrentUnitsDetect(tirStatsData.highTitMgdl) + ")",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        tirStatsData.averageTit7?.let { avg ->
            TirTableDataRow(
                date = "7d",
                below = formatPercentage(avg.below, avg.count),
                inRange = formatPercentage(avg.inRange, avg.count),
                above = formatPercentage(avg.above, avg.count),
                isBold = true
            )
        }

        tirStatsData.averageTit30?.let { avg ->
            TirTableDataRow(
                date = "30d",
                below = formatPercentage(avg.below, avg.count),
                inRange = formatPercentage(avg.inRange, avg.count),
                above = formatPercentage(avg.above, avg.count),
                isBold = true
            )
        }
    }
}

/**
 * Composable that displays the header row of the TIR statistics table.
 */
@Composable
fun TirTableHeaderRow() {
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
            text = "Below",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        Text(
            text = "In Range",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        Text(
            text = "Above",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Composable that displays a single data row in the TIR statistics table.
 *
 * @param date The date string to display
 * @param below Below range percentage
 * @param inRange In range percentage
 * @param above Above range percentage
 * @param isBold Whether to display text in bold
 */
@Composable
fun TirTableDataRow(
    date: String,
    below: String,
    inRange: String,
    above: String,
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
            text = below,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        Text(
            text = inRange,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        Text(
            text = above,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Formats a TIR value as a percentage string.
 */
private fun formatPercentage(value: Int, total: Int): String {
    return if (total > 0) {
        String.format(Locale.getDefault(), "%.0f%%", value * 100.0 / total)
    } else {
        "-"
    }
}
