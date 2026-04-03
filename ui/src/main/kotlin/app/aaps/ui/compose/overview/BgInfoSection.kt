package app.aaps.ui.compose.overview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.overview.graph.BgInfoData
import app.aaps.core.interfaces.overview.graph.BgRange
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * Displays current BG information in a circular design.
 * Shows BG value centered in a ring, with trend indicated by an arc position.
 *
 * @param bgInfo Current BG info data, or null if no data available
 * @param timeAgoText Formatted "time ago" string (e.g., "2 min")
 * @param modifier Optional modifier for the composable
 * @param size Size of the circular BG display
 */
@Composable
fun BgInfoSection(
    bgInfo: BgInfoData?,
    timeAgoText: String,
    modifier: Modifier = Modifier,
    size: Dp = AapsSpacing.bgCircleSize
) {
    if (bgInfo == null) {
        // Show placeholder when no data
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.size(size)
        ) {
            Text(
                text = "---",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val bgColor = bgInfo.bgRange.toColor()
    val ringColor = bgColor.copy(alpha = 0.3f)

    // Build accessibility description: "BG 120, Flat, delta +2, 2 min ago"
    val a11yDescription = buildString {
        append("BG ${bgInfo.bgText}")
        append(", ${bgInfo.trendDescription}")
        bgInfo.deltaText?.let { append(", delta $it") }
        if (timeAgoText.isNotEmpty()) append(", $timeAgoText ago")
        if (bgInfo.isOutdated) append(", outdated")
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .padding(AapsSpacing.small)
            .semantics { contentDescription = a11yDescription }
    ) {
        // Background ring + trend arc indicator
        Canvas(modifier = Modifier.size(size)) {
            val strokeWidth = AapsSpacing.bgRingStrokeWidth.toPx()
            val arcSize = Size(size.toPx() - strokeWidth, size.toPx() - strokeWidth)
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

            // Background ring (full circle, semi-transparent)
            drawArc(
                color = ringColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Trend arc indicator (bright segment + outward triangles)
            bgInfo.trendArrow?.let { trend ->
                trend.toArcIndicator()?.let { indicator ->
                    // Single bright arc segment
                    val arcStart = indicator.centerAngle - indicator.sweepAngle / 2
                    drawArc(
                        color = bgColor,
                        startAngle = arcStart,
                        sweepAngle = indicator.sweepAngle,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Triangles pointing outward, each placed on the circle at its own angle
                    val ringCenterX = topLeft.x + arcSize.width / 2
                    val ringCenterY = topLeft.y + arcSize.height / 2
                    val ringRadius = arcSize.width / 2
                    val triHeight = strokeWidth * 1.2f
                    val triHalfBase = strokeWidth * 1.6f
                    val n = indicator.triangleCount
                    val baseDist = ringRadius + strokeWidth * 0.2f
                    val angularSpacing = Math.toDegrees((triHalfBase * 1.6 / ringRadius)).toFloat()

                    for (i in 0 until n) {
                        val triAngle = indicator.centerAngle + (i - (n - 1) / 2f) * angularSpacing
                        val triRad = Math.toRadians(triAngle.toDouble())
                        val dirX = cos(triRad).toFloat()
                        val dirY = sin(triRad).toFloat()
                        val perpX = -dirY
                        val perpY = dirX
                        val baseX = ringCenterX + baseDist * dirX
                        val baseY = ringCenterY + baseDist * dirY
                        val tipX = baseX + triHeight * dirX
                        val tipY = baseY + triHeight * dirY
                        drawPath(
                            path = Path().apply {
                                moveTo(tipX, tipY)
                                lineTo(baseX + triHalfBase * perpX, baseY + triHalfBase * perpY)
                                lineTo(baseX - triHalfBase * perpX, baseY - triHalfBase * perpY)
                                close()
                            },
                            color = bgColor
                        )
                    }
                }
            }
        }

        // Center content: delta on top, BG value, time ago below
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = AapsSpacing.small),
            verticalArrangement = Arrangement.spacedBy((-2).dp, Alignment.CenterVertically)
        ) {
            // Delta on top
            bgInfo.deltaText?.let { delta ->
                Text(
                    text = delta,
                    style = AapsTheme.typography.bgSecondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // BG value - large bold text with strikethrough if outdated
            Text(
                text = bgInfo.bgText,
                style = AapsTheme.typography.bgValue,
                color = bgColor,
                textDecoration = if (bgInfo.isOutdated) TextDecoration.LineThrough else TextDecoration.None
            )

            // Time ago below
            Text(
                text = timeAgoText,
                style = AapsTheme.typography.bgTimeAgo,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Maps BgRange to the appropriate theme color.
 */
@Composable
private fun BgRange.toColor(): Color = when (this) {
    BgRange.HIGH     -> AapsTheme.generalColors.bgHigh
    BgRange.IN_RANGE -> AapsTheme.generalColors.bgInRange
    BgRange.LOW      -> AapsTheme.generalColors.bgLow
}

/**
 * Arc indicator describing position and triangle count.
 * @param centerAngle center of the arc in degrees (0° = right/3 o'clock, -90° = top, 90° = bottom)
 * @param sweepAngle arc length in degrees
 * @param triangleCount number of outward-pointing triangles (1-3)
 */
private data class ArcIndicator(
    val centerAngle: Float,
    val sweepAngle: Float,
    val triangleCount: Int
)

/**
 * Maps TrendArrow to arc indicator with 5 fixed positions and 1-3 triangles.
 * Returns null for NONE (no arc drawn).
 *
 * Positions: Up (-90°), 45°-up (-45°), Flat (0°), 45°-down (45°), Down (90°)
 * Double/Triple use same position but show 2 or 3 triangles.
 */
private fun TrendArrow.toArcIndicator(): ArcIndicator? {
    val sweepAngle = 40f
    return when (this) {
        TrendArrow.NONE            -> null
        TrendArrow.FLAT            -> ArcIndicator(centerAngle = 0f, sweepAngle = sweepAngle, triangleCount = 1)
        TrendArrow.FORTY_FIVE_UP   -> ArcIndicator(centerAngle = -45f, sweepAngle = sweepAngle, triangleCount = 1)
        TrendArrow.FORTY_FIVE_DOWN -> ArcIndicator(centerAngle = 45f, sweepAngle = sweepAngle, triangleCount = 1)
        TrendArrow.SINGLE_UP       -> ArcIndicator(centerAngle = -90f, sweepAngle = sweepAngle, triangleCount = 1)
        TrendArrow.SINGLE_DOWN     -> ArcIndicator(centerAngle = 90f, sweepAngle = sweepAngle, triangleCount = 1)
        TrendArrow.DOUBLE_UP       -> ArcIndicator(centerAngle = -90f, sweepAngle = sweepAngle, triangleCount = 2)
        TrendArrow.DOUBLE_DOWN     -> ArcIndicator(centerAngle = 90f, sweepAngle = sweepAngle, triangleCount = 2)
        TrendArrow.TRIPLE_UP       -> ArcIndicator(centerAngle = -90f, sweepAngle = sweepAngle, triangleCount = 3)
        TrendArrow.TRIPLE_DOWN     -> ArcIndicator(centerAngle = 90f, sweepAngle = sweepAngle, triangleCount = 3)
    }
}

@Preview(showBackground = true)
@Composable
private fun BgInfoSectionInRangePreview() {
    MaterialTheme {
        BgInfoSection(
            bgInfo = BgInfoData(
                bgValue = 120.0,
                bgText = "120",
                bgRange = BgRange.IN_RANGE,
                isOutdated = false,
                timestamp = System.currentTimeMillis(),
                trendArrow = TrendArrow.FLAT,
                trendDescription = "Flat",
                delta = 2.0,
                deltaText = "+2",
                shortAvgDelta = 1.5,
                shortAvgDeltaText = "+1.5",
                longAvgDelta = 1.0,
                longAvgDeltaText = "+1.0"
            ),
            timeAgoText = "2 min"
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BgInfoSectionHighPreview() {
    MaterialTheme {
        BgInfoSection(
            bgInfo = BgInfoData(
                bgValue = 220.0,
                bgText = "220",
                bgRange = BgRange.HIGH,
                isOutdated = false,
                timestamp = System.currentTimeMillis(),
                trendArrow = TrendArrow.DOUBLE_UP,
                trendDescription = "Rising fast",
                delta = 15.0,
                deltaText = "+15",
                shortAvgDelta = 12.0,
                shortAvgDeltaText = "+12",
                longAvgDelta = 10.0,
                longAvgDeltaText = "+10"
            ),
            timeAgoText = "1 min"
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BgInfoSectionLowPreview() {
    MaterialTheme {
        BgInfoSection(
            bgInfo = BgInfoData(
                bgValue = 65.0,
                bgText = "65",
                bgRange = BgRange.LOW,
                isOutdated = false,
                timestamp = System.currentTimeMillis(),
                trendArrow = TrendArrow.TRIPLE_DOWN,
                trendDescription = "Falling rapidly",
                delta = -10.0,
                deltaText = "-10",
                shortAvgDelta = -8.0,
                shortAvgDeltaText = "-8",
                longAvgDelta = -6.0,
                longAvgDeltaText = "-6"
            ),
            timeAgoText = "3 min"
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BgInfoSectionNullPreview() {
    MaterialTheme {
        BgInfoSection(
            bgInfo = null,
            timeAgoText = ""
        )
    }
}
