package app.aaps.ui.compose.overview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.overview.graph.BgInfoData
import app.aaps.core.interfaces.overview.graph.BgRange
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalAapsScale
import app.aaps.ui.compose.components.bgSpokenDescription
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Displays current BG information in a circular design.
 * Shows BG value centered in a ring, with trend indicated by an arc position.
 *
 * @param bgInfo Current BG info data, or null if no data available
 * @param timeAgoText Formatted "time ago" string in the user's language, including the word "ago"
 *   (`DateUtil.minAgo`, e.g. "2 min ago")
 * @param modifier Optional modifier for the composable
 * @param size Size of the circular BG display. Defaults to [AapsSpacing.bgCircleSize] scaled by
 *   [LocalAapsScale] so the circle grows on tablets to match scaled typography. Pass an explicit
 *   value only if you want to override the tablet-aware default.
 * @param showTimeAgo Whether to render the small "time ago" line below the BG value.
 *
 * @see BgInfoSectionInRangePreview
 * @see BgInfoSectionHighPreview
 * @see BgInfoSectionLowPreview
 * @see BgInfoSectionNullPreview
 */
@Composable
fun BgInfoSection(
    bgInfo: BgInfoData?,
    timeAgoText: String,
    modifier: Modifier = Modifier,
    size: Dp = AapsSpacing.bgCircleSize * LocalAapsScale.current,
    showTimeAgo: Boolean = true
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
    val ringStrokeWidth = AapsSpacing.bgRingStrokeWidth * LocalAapsScale.current

    // High/low is the colour and an old reading is struck through, so the description says both.
    val a11yDescription = bgSpokenDescription(
        bgText = bgInfo.bgText,
        range = bgInfo.bgRange,
        trend = bgInfo.trendDescription,
        deltaText = bgInfo.deltaText,
        timeAgo = timeAgoText,
        isOutdated = bgInfo.isOutdated
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .padding(AapsSpacing.small)
            // Merging, not a bare semantics block: a described node is reached by linear screen-reader
            // navigation only when it is a leaf or a merging parent. This one has the value, the delta
            // and the age as children, so unmerged the whole sentence was skipped and those three were
            // read instead, as loose fragments mixed in among the chips - measured with TalkBack on a
            // phone. Merging makes the sentence one stop and takes the fragments out of the sweep.
            .semantics(mergeDescendants = true) { contentDescription = a11yDescription }
    ) {
        // Background ring + trend arc indicator
        Canvas(modifier = Modifier.size(size)) {
            val strokeWidth = ringStrokeWidth.toPx()
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
                    // Same expressions the JDK's Math.toDegrees / Math.toRadians use, written out
                    // so they work on every platform. Note the order: toRadians divides first.
                    val angularSpacing = ((triHalfBase * 1.6 / ringRadius) * 180.0 / PI).toFloat()

                    for (i in 0 until n) {
                        val triAngle = indicator.centerAngle + (i - (n - 1) / 2f) * angularSpacing
                        val triRad = triAngle.toDouble() / 180.0 * PI
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
        LinesInsideRing(
            circleSize = size,
            ringStrokeWidth = ringStrokeWidth,
            delta = {
                bgInfo.deltaText?.let { delta ->
                    Text(
                        text = delta,
                        style = AapsTheme.typography.bgSecondary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            value = {
                // The ring does not grow with the system font and the value already fills it, so the
                // value keeps its normal size. The two small lines follow the system font.
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1f)) {
                    // BG value - large bold text with strikethrough if outdated
                    Text(
                        text = bgInfo.bgText,
                        style = AapsTheme.typography.bgValue,
                        color = bgColor,
                        textDecoration = if (bgInfo.isOutdated) TextDecoration.LineThrough else TextDecoration.None
                    )
                }
            },
            timeAgo = {
                if (showTimeAgo) {
                    Text(
                        text = timeAgoText,
                        style = AapsTheme.typography.bgTimeAgo,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }
}

/**
 * Places the delta, the BG value and the time-ago line inside the ring: the value in the middle, the
 * other two just above and below it. A line that would reach into the ring is drawn smaller, each line
 * on its own. So a large system font or a long "time ago" text (French, Romanian) makes only that line
 * smaller, never the value.
 */
@Composable
private fun LinesInsideRing(
    circleSize: Dp,
    ringStrokeWidth: Dp,
    delta: @Composable () -> Unit,
    value: @Composable () -> Unit,
    timeAgo: @Composable () -> Unit
) {
    Layout(
        contents = listOf(delta, value, timeAgo),
        modifier = Modifier.size(circleSize)
    ) { (deltaParts, valueParts, timeAgoParts), _ ->
        val anySize = Constraints()
        val deltaLine = deltaParts.firstOrNull()?.measure(anySize)
        val valueLine = valueParts.first().measure(anySize)
        val timeAgoLine = timeAgoParts.firstOrNull()?.measure(anySize)

        val side = circleSize.roundToPx()
        val hole = (circleSize / 2 - ringStrokeWidth).toPx()
        // The lines sit a little above the middle, and the small lines overlap the value a little
        val lift = AapsSpacing.extraSmall.toPx()
        val overlap = AapsSpacing.extraSmall.toPx()

        layout(side, side) {
            val ringCenter = side / 2f
            val valueCenter = ringCenter - lift
            // The value is centred, so its far edge is half of its height further out than the lift
            val valueScale = scaleToFit(valueLine.width, lift, valueLine.height / 2f * NUMBERS_INK, hole)
            val valueTop = valueCenter - valueLine.height * valueScale / 2
            val valueBottom = valueCenter + valueLine.height * valueScale / 2
            placeScaled(valueLine, ringCenter, valueCenter, valueScale)

            deltaLine?.let { line ->
                val bottom = valueTop + overlap
                val scale = scaleToFit(line.width, ringCenter - bottom, line.height * NUMBERS_INK, hole)
                placeScaled(line, ringCenter, bottom - line.height * scale / 2, scale)
            }
            timeAgoLine?.let { line ->
                val top = valueBottom - overlap
                // Words: letters like "p" and "g" go down to the bottom of the line, also at its ends
                val scale = scaleToFit(line.width, top - ringCenter, line.height.toFloat(), hole)
                placeScaled(line, ringCenter, top + line.height * scale / 2, scale)
            }
        }
    }
}

/**
 * Numbers do not fill their line: there is nothing above the top of a digit, and only the comma goes
 * below it, in the middle. So the outer part of a line of numbers, about 15 %, stays empty and may
 * reach the ring. With 20 % the delta touched the ring in a test at three times its size.
 */
private const val NUMBERS_INK = 0.85f

/**
 * The biggest scale, at most 1, at which a line stays inside a hole of [hole] radius. The line is
 * [width] wide, its edge nearest to the centre is [near] from the centre, and at full size its ink
 * reaches [ink] further out. Solves (width·s/2)² + (near + ink·s)² = hole² for s.
 */
private fun scaleToFit(width: Int, near: Float, ink: Float, hole: Float): Float {
    if (near >= hole) return 0f
    val a = width * width / 4f + ink * ink
    val b = near * ink
    val c = near * near - hole * hole
    return ((-b + sqrt(b * b - a * c)) / a).coerceAtMost(1f)
}

/** Places [line] with its centre at [centerX], [centerY], drawn at [scale] of its size. */
private fun Placeable.PlacementScope.placeScaled(line: Placeable, centerX: Float, centerY: Float, scale: Float) {
    val x = (centerX - line.width / 2f).roundToInt()
    val y = (centerY - line.height / 2f).roundToInt()
    if (scale >= 1f) line.place(x, y)
    else line.placeWithLayer(x, y) {
        scaleX = scale
        scaleY = scale
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
 * Arc indicator describing position and triangle count. Internal: also used by the glucose circle widget.
 * @param centerAngle center of the arc in degrees (0° = right/3 o'clock, -90° = top, 90° = bottom)
 * @param sweepAngle arc length in degrees
 * @param triangleCount number of outward-pointing triangles (1-3)
 */
internal data class ArcIndicator(
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
internal fun TrendArrow.toArcIndicator(): ArcIndicator? {
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
