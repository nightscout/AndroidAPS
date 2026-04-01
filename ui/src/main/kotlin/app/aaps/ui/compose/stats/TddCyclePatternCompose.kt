package app.aaps.ui.compose.stats

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.NumberInputRow
import app.aaps.ui.R
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.vicoTheme

@Immutable
data class TddCyclePatternData(
    val rawCycles: List<CycleSeries>,
    val cleanedCycles: List<CycleSeries>,
    val rawAverage: List<Double>,
    val cleanedAverage: List<Double>,
    val cycleCount: Int,
    val totalDaysAvailable: Int
)

@Immutable
data class CycleSeries(
    val dayValues: List<Double>,
    val cycleIndex: Int,
    val startDate: String
)

@Composable
fun TddCyclePatternCompose(
    data: TddCyclePatternData,
    offset: Int,
    onOffsetChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCleaned by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.cycles_found, data.cycleCount, data.totalDaysAvailable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            FilterChip(
                selected = !showCleaned,
                onClick = { showCleaned = false },
                label = { Text(stringResource(R.string.raw_tdd)) },
                modifier = Modifier.padding(end = 8.dp)
            )
            FilterChip(
                selected = showCleaned,
                onClick = { showCleaned = true },
                label = { Text(stringResource(R.string.cleaned_tdd)) }
            )
        }

        if (showCleaned) {
            Text(
                text = stringResource(R.string.cleaned_tdd_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Show Day 1 date from newest cycle
        val cycles = if (showCleaned) data.cleanedCycles else data.rawCycles
        if (cycles.isNotEmpty()) {
            Text(
                text = stringResource(R.string.cycle_day1_date, cycles.first().startDate),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Crossfade(targetState = showCleaned, label = "tdd_cycle_view") { cleaned ->
            val shownCycles = if (cleaned) data.cleanedCycles else data.rawCycles
            val average = if (cleaned) data.cleanedAverage else data.rawAverage
            val baseColor = if (cleaned) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
            TddCycleChart(
                cycles = shownCycles,
                average = average,
                baseColor = baseColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
        }

        // Legend
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        ) {
            val legendBaseColor = if (showCleaned) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(legendBaseColor.copy(alpha = 0.5f), CircleShape)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.cycle_lines_legend),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(AapsTheme.generalColors.cycleAverage, CircleShape)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(app.aaps.core.ui.R.string.average),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Offset slider
        NumberInputRow(
            labelResId = R.string.cycle_offset,
            value = offset.toDouble(),
            onValueChange = { onOffsetChange(it.toInt()) },
            valueRange = 0.0..27.0,
            step = 1.0,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TddCycleChart(
    cycles: List<CycleSeries>,
    average: List<Double>,
    baseColor: Color,
    modifier: Modifier = Modifier
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val averageColor = AapsTheme.generalColors.cycleAverage
    val cycleCount = cycles.size

    // x values 0..27, formatter shows day 1..28
    val xValues = (0..27).map { it.toDouble() }

    LaunchedEffect(cycles, average) {
        if (cycles.isEmpty()) return@LaunchedEffect
        modelProducer.runTransaction {
            lineSeries {
                for (cycle in cycles) {
                    series(x = xValues, y = cycle.dayValues)
                }
                series(x = xValues, y = average)
            }
        }
    }

    // Build lines list matching series order
    // Cycle lines: thin and semi-transparent so average stands out
    // Newest cycle = most visible, oldest = faintest
    val lines = remember(cycleCount, baseColor, averageColor) {
        buildList {
            for (i in 0 until cycleCount) {
                val alpha = 0.5f - i * 0.35f / cycleCount.coerceAtLeast(1)
                add(
                    LineCartesianLayer.Line(
                        fill = LineCartesianLayer.LineFill.single(Fill(baseColor.copy(alpha = alpha.coerceAtLeast(0.1f)))),
                        stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 1.dp)
                    )
                )
            }
            // Average line: bold, dashed, distinct green color
            add(
                LineCartesianLayer.Line(
                    fill = LineCartesianLayer.LineFill.single(Fill(averageColor)),
                    stroke = LineCartesianLayer.LineStroke.Dashed(
                        thickness = 3.dp,
                        cap = StrokeCap.Round,
                        dashLength = 10.dp,
                        gapLength = 6.dp
                    )
                )
            )
        }
    }

    val dayFormatter = remember {
        CartesianValueFormatter { _, value, _ -> "${value.toInt() + 1}" }
    }

    val axisLabelComponent = rememberTextComponent(style = TextStyle(color = vicoTheme.textColor))

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(lines)
            ),
            startAxis = VerticalAxis.rememberStart(
                label = axisLabelComponent
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = dayFormatter,
                label = axisLabelComponent
            )
        ),
        modelProducer = modelProducer,
        zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content),
        modifier = modifier
    )
}
