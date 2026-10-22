package app.aaps.ui.compose.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalProfileUtil
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings

@Composable
fun AGPStats(
    selectedRange: AgpStatsRange,
    onSelectRange: (AgpStatsRange) -> Unit,
    data: AgpStatsData?,
    isLoading: Boolean,
    hasError: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.large)
    ) {
        PrimaryTabRow(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            selectedTabIndex = selectedRange.ordinal,
            containerColor = Color.Transparent,
        ) {
            AgpStatsRange.entries.forEach { range ->
                Tab(
                    selected = selectedRange == range,
                    onClick = { onSelectRange(range) },
                    text = {
                        Text(
                            text = range.label(),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                )
            }
        }
        when {
            isLoading -> Box(
                modifier = Modifier.fillMaxWidth().height(AapsSpacing.bgCircleSize),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            hasError -> {
                Text(stringResource(UiStrings.agp_load_failed), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { onSelectRange(selectedRange) }) {
                    Text(stringResource(UiStrings.recalculate))
                }
            }

            data == null || data.readingCount == 0 -> Text(
                stringResource(UiStrings.agp_no_data),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            else -> {
                AgpChart(data)
                HorizontalDivider()
                AgpGlycemicOverview(data)
                HorizontalDivider()
                AgpMetrics(data)
            }
        }
    }
}

@Composable
private fun AgpStatsRange.label(): String = when (this) {
    AgpStatsRange.TODAY    -> stringResource(UiStrings.agp_today)
    AgpStatsRange.HOURS_24 -> stringResource(UiStrings.agp_hours, 24)
    AgpStatsRange.DAYS_7   -> stringResource(UiStrings.agp_days, 7)
    AgpStatsRange.DAYS_30  -> stringResource(UiStrings.agp_days, 30)
    AgpStatsRange.DAYS_90  -> stringResource(UiStrings.agp_days, 90)
}

@Composable
private fun AgpChart(data: AgpStatsData) {
    val profileUtil = LocalProfileUtil.current
    val colors = AapsTheme.generalColors
    val wideBandColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    val narrowBandColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
    val medianColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val highThresholdColor = MaterialTheme.colorScheme.tertiary
    val tightHighMgdl = 140.0
    val percentiles = data.hourlyPercentiles
    val highestValue = maxOf(data.highMgdl, percentiles.maxOfOrNull { it.p90Mgdl } ?: data.highMgdl)
    val yAxis = calculateAgpYAxis(highestValue, profileUtil.units)
    val chartDescription = stringResource(UiStrings.agp_chart_description)
    val chartHeight = AapsSpacing.bgCircleSize + AapsSpacing.bgCircleSize / 2
    val axisWidth = AapsSpacing.xxLarge + AapsSpacing.extraLarge

    Text(stringResource(UiStrings.agp_title), style = MaterialTheme.typography.titleSmall)
    Text(
        text = profileUtil.units.displayLabel,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
    ) {
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(chartHeight)
                .semantics { contentDescription = chartDescription }
        ) {
            val strokeWidth = AapsSpacing.extraSmall.toPx()
            val gridWidth = strokeWidth / 2f
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(AapsSpacing.medium.toPx(), AapsSpacing.small.toPx()))
            fun yPosition(valueMgdl: Double): Float =
                size.height * (1f - ((valueMgdl - yAxis.minimumMgdl) / (yAxis.maximumMgdl - yAxis.minimumMgdl)).toFloat())

            fun xPosition(hour: Int): Float = size.width * hour / 24f

            repeat(5) { index ->
                val y = size.height * index / 4f
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = gridWidth)
            }
            for (hour in 0..21 step 3) {
                val x = xPosition(hour)
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = gridWidth, pathEffect = dashEffect)
            }

            fun drawThreshold(valueMgdl: Double, color: Color) {
                if (valueMgdl !in yAxis.minimumMgdl..yAxis.maximumMgdl) return
                val y = yPosition(valueMgdl)
                drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = strokeWidth, pathEffect = dashEffect)
            }

            drawThreshold(data.lowMgdl, colors.bgLow)
            drawThreshold(tightHighMgdl, colors.bgInRange)
            drawThreshold(data.highMgdl, highThresholdColor)

            fun drawBand(
                lower: (AgpHourlyPercentile) -> Double,
                upper: (AgpHourlyPercentile) -> Double,
                color: Color
            ) {
                val path = Path()
                percentiles.forEachIndexed { index, point ->
                    val x = xPosition(point.hour)
                    val y = yPosition(upper(point))
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                percentiles.asReversed().forEach { point ->
                    path.lineTo(xPosition(point.hour), yPosition(lower(point)))
                }
                path.close()
                drawPath(path, color)
            }

            drawBand(AgpHourlyPercentile::p10Mgdl, AgpHourlyPercentile::p90Mgdl, wideBandColor)
            drawBand(AgpHourlyPercentile::p25Mgdl, AgpHourlyPercentile::p75Mgdl, narrowBandColor)
            val medianPath = Path()
            percentiles.forEachIndexed { index, point ->
                val x = xPosition(point.hour)
                val y = yPosition(point.medianMgdl)
                if (index == 0) medianPath.moveTo(x, y) else medianPath.lineTo(x, y)
            }
            drawPath(medianPath, medianColor, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
            if (percentiles.size == 1) {
                val point = percentiles.single()
                drawLine(
                    narrowBandColor,
                    Offset(xPosition(point.hour), yPosition(point.p10Mgdl)),
                    Offset(xPosition(point.hour), yPosition(point.p90Mgdl)),
                    strokeWidth = strokeWidth
                )
                drawCircle(medianColor, radius = strokeWidth, center = Offset(xPosition(point.hour), yPosition(point.medianMgdl)))
            }
        }
        Column(
            modifier = Modifier.width(axisWidth).height(chartHeight),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
        ) {
            yAxis.ticksMgdl.forEach { value ->
                Text(
                    profileUtil.fromMgdlToStringInUnits(value),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.weight(1f)) {
            for (hour in 0..21 step 3) {
                Text(
                    hour.toString(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(axisWidth + AapsSpacing.medium))
    }
    val legend = listOf(
        stringResource(UiStrings.agp_percentile_10_90) to wideBandColor,
        stringResource(UiStrings.agp_percentile_25_75) to narrowBandColor,
        stringResource(UiStrings.agp_median) to medianColor,
        profileUtil.fromMgdlToStringWithUnits(data.lowMgdl) to colors.bgLow,
        profileUtil.fromMgdlToStringWithUnits(tightHighMgdl) to colors.bgInRange,
        profileUtil.fromMgdlToStringWithUnits(data.highMgdl) to highThresholdColor
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.large),
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)
    ) {
        legend.forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(AapsSpacing.large), shape = CircleShape, color = color) {}
                Spacer(modifier = Modifier.width(AapsSpacing.small))
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AgpGlycemicOverview(data: AgpStatsData) {
    val profileUtil = LocalProfileUtil.current
    val colors = AapsTheme.generalColors
    val bands = listOf(
        data.tir.veryLow to colors.bgVeryLow,
        data.tir.low to colors.bgLow,
        data.tir.inRange to colors.bgInRange,
        data.tir.high to colors.bgHigh,
        data.tir.veryHigh to colors.bgVeryHigh
    )
    Text(stringResource(UiStrings.agp_glycemic_overview), style = MaterialTheme.typography.titleSmall)
    val description = stringResource(UiStrings.agp_tir_chart)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
            AgpOverviewValue(
                stringResource(
                    UiStrings.agp_range,
                    profileUtil.fromMgdlToStringInUnits(data.lowMgdl),
                    profileUtil.fromMgdlToStringInUnits(data.highMgdl)
                ),
                stringResource(UiStrings.agp_percent, data.tir.inRange),
                colors.bgInRange
            )
            AgpOverviewValue(
                stringResource(
                    UiStrings.agp_range,
                    profileUtil.fromMgdlToStringInUnits(70.0),
                    profileUtil.fromMgdlToStringInUnits(140.0)
                ),
                stringResource(UiStrings.agp_percent, data.tightRangePercent),
                colors.bgInRange
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
            AgpOverviewValue(
                stringResource(UiStrings.agp_above, profileUtil.fromMgdlToStringInUnits(data.highMgdl)),
                stringResource(UiStrings.agp_percent, data.tir.high + data.tir.veryHigh),
                colors.bgHigh
            )
            AgpOverviewValue(
                stringResource(UiStrings.agp_below, profileUtil.fromMgdlToStringInUnits(data.lowMgdl)),
                stringResource(UiStrings.agp_percent, data.tir.veryLow + data.tir.low),
                colors.bgVeryLow
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
            AgpOverviewValue(
                stringResource(UiStrings.agp_average),
                profileUtil.fromMgdlToStringInUnits(data.averageMgdl),
                MaterialTheme.colorScheme.onSurface
            )
            AgpOverviewValue(
                stringResource(UiStrings.agp_median),
                profileUtil.fromMgdlToStringInUnits(data.medianMgdl),
                MaterialTheme.colorScheme.onSurface
            )
        }
        Canvas(modifier = Modifier.weight(1f).aspectRatio(1f).semantics { contentDescription = description }) {
            val strokeWidth = AapsSpacing.bgRingStrokeWidth.toPx() * 2
            val inset = strokeWidth / 2f
            var startAngle = -90f
            bands.forEach { (percentage, color) ->
                val sweep = (percentage / 100.0 * 360.0).toFloat()
                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                )
                startAngle += sweep
            }
        }
    }
}

@Composable
private fun AgpOverviewValue(label: String, value: String, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
private fun AgpMetrics(data: AgpStatsData) {
    val profileUtil = LocalProfileUtil.current
    val metrics = listOf(
        stringResource(UiStrings.agp_ea1c) to formatGlycemicMetric(data.eA1cPercent, profileUtil.units),
        stringResource(UiStrings.agp_gmi) to formatGlycemicMetric(data.gmiPercent, profileUtil.units),
        stringResource(UiStrings.agp_standard_deviation) to profileUtil.fromMgdlToStringInUnits(data.standardDeviationMgdl),
        stringResource(UiStrings.agp_cv) to stringResource(UiStrings.agp_percent, data.coefficientOfVariation),
        stringResource(UiStrings.agp_available_days) to stringResource(UiStrings.agp_decimal, data.availableDays)
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
        verticalAlignment = Alignment.Top
    ) {
        metrics.forEach { (label, value) ->
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AapsSpacing.extraSmall)
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun formatGlycemicMetric(valuePercent: Double, glucoseUnit: GlucoseUnit): String =
    stringResource(
        UiStrings.agp_decimal,
        if (glucoseUnit == GlucoseUnit.MMOL) valuePercent * 10.929 - 23.5 else valuePercent
    )
