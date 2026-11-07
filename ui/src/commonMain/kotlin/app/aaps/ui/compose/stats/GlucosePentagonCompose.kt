package app.aaps.ui.compose.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.stats.DexcomTIR
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalDecimalFormatter
import app.aaps.core.ui.compose.LocalProfileUtil
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * CGP (Comprehensive Glucose Pentagon) data model.
 *
 * Based on Vigersky et al. (2018) — a radar/spider chart with 5 CGM-derived metrics.
 * Smaller pentagon = better glycemic control.
 */
@Immutable
data class CgpData(
    val torPct: Double,
    val cvPct: Double,
    val hypoPct: Double,
    val hyperPct: Double,
    val meanGlucose: Double,
    val normalizedValues: List<Double>,
    val referenceValues: List<Double>,
    val pgr: Double
)

private const val AXIS_COUNT = 5

private const val ANGLE_STEP_DEG = 72.0

/** Length of each axis in the paper. */
private const val AXIS_LENGTH_MM = 76.0

private const val MINUTES_PER_DAY = 1440.0

/**
 * Where each axis of Vigersky et al. 2018 (PMC5761978) puts its value, in mm, from the top axis clockwise.
 * All healthy values give 14 mm, which gives the reference area of 466 mm² from the paper.
 *
 * @param torMinutes minutes per day out of 70-180 mg/dL
 * @param cvPct coefficient of variation in %
 * @param hypoIntensity intensity of hypoglycemia, see [DexcomTIR.cgpHypoIntensity]
 * @param hyperIntensity intensity of hyperglycemia, see [DexcomTIR.cgpHyperIntensity]
 * @param meanMgdl mean glucose in mg/dL
 */
internal fun cgpAxesMm(torMinutes: Double, cvPct: Double, hypoIntensity: Double, hyperIntensity: Double, meanMgdl: Double): List<Double> = listOf(
    (torMinutes * 0.00614).pow(1.581) + 14,
    (cvPct - 17).coerceAtLeast(0.0) * 0.92 + 14,
    exp(hypoIntensity * 0.00057) + 13,
    (hyperIntensity * 0.000115).pow(1.51) + 14,
    ((meanMgdl - 90).coerceAtLeast(0.0) * 0.0217).pow(2.63) + 14
)

/** The area of the pentagon compared to the area of a healthy person. Not limited to the drawn axes. */
internal fun pgrOf(axesMm: List<Double>): Double = pentagonArea(axesMm) / pentagonArea(HEALTHY_AXES_MM)

private val HEALTHY_AXES_MM = List(AXIS_COUNT) { 14.0 }

/**
 * Computes CGP data from DexcomTIR statistics.
 *
 * @param dexcomTir 14-day Dexcom TIR data
 * @return CgpData with raw values, normalized values (0-1), reference values, and PGR score
 */
fun computeCgpData(dexcomTir: DexcomTIR): CgpData {
    val hypoPct = dexcomTir.cgpBelowRangePct()
    val hyperPct = dexcomTir.cgpAboveRangePct()
    val torPct = hypoPct + hyperPct
    val mean = dexcomTir.mean()
    val sd = dexcomTir.calculateSD()
    val cvPct = if (mean > 0) sd / mean * 100.0 else 0.0

    val axesMm = cgpAxesMm(torPct / 100.0 * MINUTES_PER_DAY, cvPct, dexcomTir.cgpHypoIntensity(), dexcomTir.cgpHyperIntensity(), mean)

    fun normalize(mm: Double): Double = (mm / AXIS_LENGTH_MM).coerceAtMost(1.0)

    return CgpData(
        torPct = torPct,
        cvPct = cvPct,
        hypoPct = hypoPct,
        hyperPct = hyperPct,
        meanGlucose = mean,
        normalizedValues = axesMm.map(::normalize),
        referenceValues = HEALTHY_AXES_MM.map(::normalize),
        pgr = pgrOf(axesMm)
    )
}

/**
 * Pentagon area using the shoelace formula:
 * Area = 0.5 * sin(72°) * Σ(r_i * r_{i+1})
 */
private fun pentagonArea(radii: List<Double>): Double {
    val sinAngle = sin(ANGLE_STEP_DEG * PI / 180.0)
    var sum = 0.0
    for (i in radii.indices) {
        sum += radii[i] * radii[(i + 1) % radii.size]
    }
    return 0.5 * sinAngle * sum
}

/**
 * Full CGP card content composable.
 *
 * @param dexcomTir 14-day Dexcom TIR data
 * @param modifier Modifier for the root Column
 */
@Composable
fun GlucosePentagonCompose(
    dexcomTir: DexcomTIR,
    modifier: Modifier = Modifier
) {
    val profileUtil = LocalProfileUtil.current
    val cgpData = remember(dexcomTir) { computeCgpData(dexcomTir) }
    GlucosePentagonCard(
        cgpData = cgpData,
        meanGlucoseFormatted = profileUtil.fromMgdlToStringInUnits(cgpData.meanGlucose),
        modifier = modifier
    )
}

/**
 * @see GlucosePentagonPreview
 */
@Composable
internal fun GlucosePentagonCard(
    cgpData: CgpData,
    meanGlucoseFormatted: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GlucosePentagonChart(
            cgpData = cgpData,
            meanGlucoseFormatted = meanGlucoseFormatted,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.3f)
        )

        // Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendItem(
                color = AapsTheme.generalColors.bgInRange,
                text = stringResource(UiStrings.cgp_reference)
            )
            Spacer(modifier = Modifier.size(24.dp))
            LegendItem(
                color = MaterialTheme.colorScheme.primary,
                text = stringResource(UiStrings.cgp_patient)
            )
        }

        // PGR score (thresholds from Vigersky et al. 2018)
        val pgrColor = when {
            cgpData.pgr <= 2.0 -> AapsTheme.generalColors.bgInRange
            cgpData.pgr <= 3.0 -> AapsTheme.generalColors.bgInRange
            cgpData.pgr <= 4.0 -> MaterialTheme.colorScheme.tertiary
            cgpData.pgr <= 4.5 -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
            else               -> MaterialTheme.colorScheme.error
        }
        Text(
            text = stringResource(InterfacesStrings.confirmation_line, stringResource(UiStrings.cgp_pgr), LocalDecimalFormatter.current.to1Decimal(cgpData.pgr)),
            style = MaterialTheme.typography.titleMedium,
            color = pgrColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        val pgrExplanation = when {
            cgpData.pgr <= 2.0 -> stringResource(UiStrings.cgp_pgr_very_low)
            cgpData.pgr <= 3.0 -> stringResource(UiStrings.cgp_pgr_low)
            cgpData.pgr <= 4.0 -> stringResource(UiStrings.cgp_pgr_moderate)
            cgpData.pgr <= 4.5 -> stringResource(UiStrings.cgp_pgr_high)
            else               -> stringResource(UiStrings.cgp_pgr_extremely_high)
        }
        Text(
            text = pgrExplanation,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Canvas-based radar/spider chart drawing the CGP pentagon.
 */
@Composable
fun GlucosePentagonChart(
    cgpData: CgpData,
    meanGlucoseFormatted: String,
    modifier: Modifier = Modifier
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisColor = MaterialTheme.colorScheme.outline
    val refColor = AapsTheme.generalColors.bgInRange
    val patientColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurface

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, color = labelColor, textAlign = TextAlign.Center)

    // Axis labels and value strings
    val axisLabels = listOf(
        stringResource(UiStrings.cgp_tor),
        stringResource(UiStrings.cgp_cv),
        stringResource(UiStrings.cgp_hypo),
        stringResource(UiStrings.cgp_hyper),
        stringResource(UiStrings.cgp_mean_glucose)
    )
    val decimalFormatter = LocalDecimalFormatter.current
    val axisValues = listOf(
        stringResource(CoreUiStrings.units_format_percent_value, decimalFormatter.to0Decimal(cgpData.torPct)),
        stringResource(CoreUiStrings.units_format_percent_value, decimalFormatter.to1Decimal(cgpData.cvPct)),
        stringResource(CoreUiStrings.units_format_percent_value, decimalFormatter.to1Decimal(cgpData.hypoPct)),
        stringResource(CoreUiStrings.units_format_percent_value, decimalFormatter.to1Decimal(cgpData.hyperPct)),
        meanGlucoseFormatted
    )

    Canvas(modifier = modifier) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val radius = min(centerX, centerY) * 0.72f

        // Draw concentric grid pentagons (25%, 50%, 75%, 100%)
        for (level in 1..4) {
            val fraction = level / 4f
            drawPentagonPath(centerX, centerY, radius * fraction, gridColor, style = Stroke(width = 1f))
        }

        // Draw axis lines
        for (i in 0 until AXIS_COUNT) {
            val angle = axisAngleRad(i)
            val endX = centerX + radius * cos(angle).toFloat()
            val endY = centerY + radius * sin(angle).toFloat()
            drawLine(
                color = axisColor,
                start = Offset(centerX, centerY),
                end = Offset(endX, endY),
                strokeWidth = 1f
            )
        }

        // Reference pentagon (green)
        drawDataPentagon(
            centerX, centerY, radius,
            cgpData.referenceValues,
            fillColor = refColor.copy(alpha = 0.20f),
            strokeColor = refColor,
            strokeWidth = 2f
        )

        // Patient pentagon (primary)
        drawDataPentagon(
            centerX, centerY, radius,
            cgpData.normalizedValues,
            fillColor = patientColor.copy(alpha = 0.25f),
            strokeColor = patientColor,
            strokeWidth = 2.5f
        )

        // Draw axis labels and values
        for (i in 0 until AXIS_COUNT) {
            val angle = axisAngleRad(i)
            val labelRadius = radius * 1.12f
            val labelX = centerX + labelRadius * cos(angle).toFloat()
            val labelY = centerY + labelRadius * sin(angle).toFloat()

            val labelText = axisLabels[i]
            val valueText = axisValues[i]
            val combined = "$labelText\n$valueText"

            val measuredText = textMeasurer.measure(combined, labelStyle.copy(fontSize = 10.sp))
            val textWidth = measuredText.size.width.toFloat()
            val textHeight = measuredText.size.height.toFloat()

            drawText(
                textMeasurer = textMeasurer,
                text = combined,
                topLeft = Offset(labelX - textWidth / 2f, labelY - textHeight / 2f),
                style = labelStyle.copy(fontSize = 10.sp)
            )
        }
    }
}

/**
 * Returns the angle in radians for axis index i.
 * Axis 0 (ToR) starts at the top (-90°), each subsequent axis is 72° clockwise.
 */
private fun axisAngleRad(i: Int): Double =
    (-90.0 + i * ANGLE_STEP_DEG) * PI / 180.0

/**
 * Draws a pentagon outline or filled path at a given radius.
 */
private fun DrawScope.drawPentagonPath(
    cx: Float, cy: Float, radius: Float,
    color: Color,
    style: Stroke
) {
    val path = Path()
    for (i in 0 until AXIS_COUNT) {
        val angle = axisAngleRad(i)
        val x = cx + radius * cos(angle).toFloat()
        val y = cy + radius * sin(angle).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color, style = style)
}

/**
 * Draws a data pentagon (filled + stroked) with per-axis radii.
 */
private fun DrawScope.drawDataPentagon(
    cx: Float, cy: Float, maxRadius: Float,
    normalizedValues: List<Double>,
    fillColor: Color,
    strokeColor: Color,
    strokeWidth: Float
) {
    val path = Path()
    for (i in normalizedValues.indices) {
        val angle = axisAngleRad(i)
        val r = maxRadius * normalizedValues[i].toFloat()
        val x = cx + r * cos(angle).toFloat()
        val y = cy + r * sin(angle).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, fillColor, style = Fill)
    drawPath(path, strokeColor, style = Stroke(width = strokeWidth))
}

/**
 * Small legend dot + label composable.
 */
@Composable
fun LegendItem(
    color: Color,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(modifier = Modifier) {
            Canvas(modifier = Modifier.size(10.dp)) {
                drawCircle(color = color)
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
