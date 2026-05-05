package app.aaps.ui.compose.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.stats.DexcomTIR
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalProfileUtil
import kotlin.math.roundToInt

/**
 * Composable that displays Dexcom Time In Range (TIR) statistics.
 * Shows detailed 14-day TIR statistics with 5 ranges, HbA1c and standard deviation.
 *
 * @param dexcomTir Dexcom TIR data
 * @param modifier Modifier for the root Column
 */
@Composable
fun DexcomTirStatsCompose(
    dexcomTir: DexcomTIR,
    modifier: Modifier = Modifier
) {
    val profileUtil = LocalProfileUtil.current
    Column(modifier = modifier) {
        // Header with ranges
        Text(
            text = buildString {
                append(stringResource(R.string.detailed_14_days))
                append("\n")
                append(stringResource(R.string.day_tir))
                append(" (")
                append(profileUtil.fromMgdlToStringInUnits(0.0))
                append("-")
                append(profileUtil.stringInCurrentUnitsDetect(dexcomTir.veryLowTirMgdl()))
                append("-")
                append(profileUtil.stringInCurrentUnitsDetect(dexcomTir.lowTirMgdl()))
                append("-")
                append(profileUtil.stringInCurrentUnitsDetect(dexcomTir.highTirMgdl()))
                append("-")
                append(profileUtil.stringInCurrentUnitsDetect(dexcomTir.veryHighTirMgdl()))
                append("-∞)\n")
                append(stringResource(R.string.night_tir))
                append(" (")
                append(profileUtil.fromMgdlToStringInUnits(0.0))
                append("-")
                append(profileUtil.stringInCurrentUnitsDetect(dexcomTir.veryLowTirMgdl()))
                append("-")
                append(profileUtil.stringInCurrentUnitsDetect(dexcomTir.lowTirMgdl()))
                append("-")
                append(profileUtil.stringInCurrentUnitsDetect(dexcomTir.highNightTirMgdl()))
                append("-")
                append(profileUtil.stringInCurrentUnitsDetect(dexcomTir.veryHighTirMgdl()))
                append("-∞)")
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )

        // Table header
        DexcomTirTableHeaderRow()

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // Data row
        DexcomTirTableDataRow(
            veryLow = formatPercent(dexcomTir.veryLowPct()),
            low = formatPercent(dexcomTir.lowPct()),
            inRange = formatPercent(dexcomTir.inRangePct()),
            high = formatPercent(dexcomTir.highPct()),
            veryHigh = formatPercent(dexcomTir.veryHighPct())
        )

        // Standard deviation
        if (dexcomTir.count() > 0) {
            Text(
                text = stringResource(R.string.std_deviation, profileUtil.fromMgdlToStringInUnits(dexcomTir.calculateSD())),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            )

            // HbA1c
            val mean = dexcomTir.mean()
            val hba1cPercent = (10 * (mean + 46.7) / 28.7).roundToInt() / 10.0
            val hba1cMmol = (((mean + 46.7) / 28.7 - 2.15) * 10.929).roundToInt()

            Text(
                text = buildString {
                    append(stringResource(R.string.hba1c))
                    append(hba1cPercent)
                    append("% (")
                    append(hba1cMmol)
                    append(" mmol/mol)")
                },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )

            DexcomTirBarCompose(
                veryLowPct = dexcomTir.veryLowPct(),
                lowPct = dexcomTir.lowPct(),
                inRangePct = dexcomTir.inRangePct(),
                highPct = dexcomTir.highPct(),
                veryHighPct = dexcomTir.veryHighPct(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            )
        }
    }
}

@Composable
private fun DexcomTirBarCompose(
    veryLowPct: Double,
    lowPct: Double,
    inRangePct: Double,
    highPct: Double,
    veryHighPct: Double,
    modifier: Modifier = Modifier
) {
    val colors = AapsTheme.generalColors
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.tir),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clip(RoundedCornerShape(4.dp))
        ) {
            BarSegment(pct = veryLowPct, color = colors.bgVeryLow)
            BarSegment(pct = lowPct, color = colors.bgLow)
            BarSegment(pct = inRangePct, color = colors.bgInRange)
            BarSegment(pct = highPct, color = colors.bgHigh)
            BarSegment(pct = veryHighPct, color = colors.bgVeryHigh)
        }
    }
}

@Composable
private fun RowScope.BarSegment(pct: Double, color: Color) {
    if (pct <= 0.0) return
    val textColor = if (color.luminance() > 0.5f) Color.Black else Color.White
    Box(
        modifier = Modifier
            .weight(pct.toFloat())
            .fillMaxHeight()
            .background(color)
            .clip(RoundedCornerShape(0.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (pct >= 8.0) {
            Text(
                text = String.format("%d%%", pct.roundToInt()),
                color = textColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

/**
 * Composable that displays the header row of the Dexcom TIR table.
 */
@Composable
private fun DexcomTirTableHeaderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(R.string.veryLow),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.low),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.in_range),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.high),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.veryHigh),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Composable that displays a data row in the Dexcom TIR table.
 */
@Composable
private fun DexcomTirTableDataRow(
    veryLow: String,
    low: String,
    inRange: String,
    high: String,
    veryHigh: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = veryLow,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        Text(
            text = low,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        Text(
            text = inRange,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        Text(
            text = high,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        Text(
            text = veryHigh,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Formats a percentage value as a string.
 */
private fun formatPercent(value: Double): String {
    return String.format("%.0f%%", value)
}
