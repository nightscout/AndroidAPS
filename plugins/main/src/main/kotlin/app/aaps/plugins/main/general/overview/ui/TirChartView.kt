package app.aaps.plugins.main.general.overview.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.res.ResourcesCompat
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.stats.TIR
import app.aaps.plugins.main.R
import javax.inject.Inject

/**
 * Data class representing a single TIR scenario row
 */
data class TirScenario(
    val subtitle: String,
    val belowPct: Double,
    val inRangePct: Double,
    val abovePct: Double,
    val unknownPct: Double = 0.0  // For worst case gray bar
)

/**
 * Data class for combined worst/best case scenario
 */
data class TirCombinedScenario(
    val subtitle: String,
    val belowPct: Double,  // Same for both scenarios
    val abovePct: Double,  // Same for both scenarios
    val worstInRangePct: Double,  // In range for worst case
    val bestInRangePct: Double,   // In range for best case
    val worstTotalMiddlePct: Double  // worstInRangePct + unknown for worst case
)

/**
 * Data class for TIR chart with multiple scenarios
 */
data class TirChartData(
    val title: String,
    val tillNowScenario: TirScenario,
    val combinedScenario: TirCombinedScenario,
    val totalCount: Int  // Total number of BG readings
)

/**
 * Custom view for displaying Time In Range (TIR) as a horizontal stacked bar chart
 * Supports multiple scenarios (till now, best case, worst case)
 */
class TirChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val belowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val inRangePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val abovePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val unknownPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val barRect = RectF()
    private var chartData: TirChartData? = null
    private var tirData: TIR? = null
    private var title: String = ""

    // Graph drawing parameters
    private val barHeight = 20f // dp
    private val textSize = 12f // sp
    private val subtitleSize = 10f // sp
    private val titleSize = 14f // sp
    private val horizontalPadding = 16f // dp
    private val verticalPadding = 8f // dp
    private val titlePadding = 8f // dp
    private val subtitlePadding = 4f // dp
    private val rowSpacing = 8f // dp (space between scenario rows)
    private val labelSpacing = 4f // dp (space between bar and label)
    private val separatorWidth = 1f // pixels (transparent gap between bars)

    // Minimum data requirements
    private val MINIMUM_REQUIRED_DATA_COUNT: Int = 10
    private val MINIMUM_WORST_IN_RANGE_PCT = 1.0

    // Helper conversions: use once to produce px values
    private fun dp(value: Float): Float = value * resources.displayMetrics.density
    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity

    init {
        // Convert dp/sp to pixels once (use helpers `dp()` and `sp()` instead of direct access)

        // Initialize paints
        val typedValue = android.util.TypedValue()

        // Below range color (red/low)
        context.theme.resolveAttribute(app.aaps.core.ui.R.attr.lowColor, typedValue, true)
        belowPaint.color = ResourcesCompat.getColor(context.resources, typedValue.resourceId, context.theme)

        // In range color (green)
        context.theme.resolveAttribute(app.aaps.core.ui.R.attr.bgInRange, typedValue, true)
        inRangePaint.color = ResourcesCompat.getColor(context.resources, typedValue.resourceId, context.theme)

        // Above range color (yellow/high)
        context.theme.resolveAttribute(app.aaps.core.ui.R.attr.highColor, typedValue, true)
        abovePaint.color = ResourcesCompat.getColor(context.resources, typedValue.resourceId, context.theme)

        // Unknown/gray color for worst case
        context.theme.resolveAttribute(app.aaps.core.ui.R.attr.defaultTextColor, typedValue, true)
        val textColor = ResourcesCompat.getColor(context.resources, typedValue.resourceId, context.theme)
        unknownPaint.color = (textColor and 0x00FFFFFF) or 0x60000000  // Semi-transparent gray

        // Text paint
        textPaint.color = textColor
        textPaint.textSize = sp(textSize)
        textPaint.textAlign = Paint.Align.CENTER

        // Subtitle paint
        subtitlePaint.color = textColor
        subtitlePaint.textSize = sp(subtitleSize)
        subtitlePaint.textAlign = Paint.Align.CENTER

        // Title paint
        titlePaint.color = textColor
        titlePaint.textSize = sp(titleSize)
        titlePaint.textAlign = Paint.Align.CENTER
        titlePaint.isFakeBoldText = true
    }

    /**
     * Set TIR data to be displayed (legacy single-row mode for history dialog)
     */
    fun setData(tir: TIR?, titleText: String) {
        this.tirData = tir
        this.title = titleText
        this.chartData = null
        invalidate()
    }

    /**
     * Set TIR chart data with multiple scenarios (for main screen)
     */
    fun setChartData(data: TirChartData?) {
        this.chartData = data
        this.tirData = null
        this.title = ""
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = if (chartData != null) {
            // Multi-scenario mode: title + till now row + combined row (same height as till now)
                        val normalRowHeight = sp(subtitleSize) + dp(subtitlePadding) +
                                                                 dp(barHeight) + dp(labelSpacing) + sp(textSize)
                        (sp(titleSize) + dp(titlePadding) +
                            normalRowHeight * 2 + dp(rowSpacing) +
                            dp(verticalPadding) * 2).toInt()
        } else {
                        // Single row mode: title + titlePadding + bar + labelSpacing + text + verticalPadding
                        (sp(titleSize) + dp(titlePadding) + dp(barHeight) +
                            dp(labelSpacing) + sp(textSize) + dp(verticalPadding) * 2).toInt()
        }

        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> desiredHeight.coerceAtMost(heightSize)
            else -> desiredHeight
        }

        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height)
    }

    private fun drawScenarioBar(
        canvas: Canvas,
        barTop: Float,
        barBottom: Float,
        barWidth: Float,
        hPadding: Float,
        labelY: Float,
        belowPct: Double,
        inRangePct: Double,
        abovePct: Double,
        unknownPct: Double = 0.0
    ) {
        // Normalize displayed percentages to ensure they add up to 100%
        var displayBelow = kotlin.math.round(belowPct).toInt()
        var displayInRange = kotlin.math.round(inRangePct).toInt()
        var displayAbove = kotlin.math.round(abovePct).toInt()
        var displayUnknown = kotlin.math.round(unknownPct).toInt()

        val sum = displayBelow + displayInRange + displayAbove + displayUnknown
        if (sum != 100 && sum > 0) {
            val remainder = 100 - sum
            // Add remainder to largest value
            when {
                displayInRange >= displayBelow && displayInRange >= displayAbove && displayInRange >= displayUnknown -> displayInRange += remainder
                displayBelow >= displayAbove && displayBelow >= displayUnknown -> displayBelow += remainder
                displayAbove >= displayUnknown -> displayAbove += remainder
                else -> displayUnknown += remainder
            }
        }

        // Count non-zero segments to calculate separators needed
        val nonZeroSegments = listOf(belowPct > 0, inRangePct > 0, abovePct > 0, unknownPct > 0).count { it }
        val totalSeparatorWidth = if (nonZeroSegments > 1) (nonZeroSegments - 1) * separatorWidth else 0f
        val availableBarWidth = barWidth - totalSeparatorWidth

        var currentX = hPadding
        var isFirstSegment = true

        // Draw below range bar
        if (belowPct > 0) {
            val segmentWidth = (availableBarWidth * belowPct / 100f).toFloat().coerceAtLeast(1f)
            barRect.set(currentX, barTop, currentX + segmentWidth, barBottom)
            canvas.drawRect(barRect, belowPaint)

            val textX = currentX + segmentWidth / 2f
            canvas.drawText(String.format("%d%%", displayBelow), textX, labelY, textPaint)

            currentX += segmentWidth
            isFirstSegment = false
        }

        // Draw in range bar
        if (inRangePct > 0) {
            if (!isFirstSegment) currentX += separatorWidth
            val segmentWidth = (availableBarWidth * inRangePct / 100f).toFloat().coerceAtLeast(1f)
            barRect.set(currentX, barTop, currentX + segmentWidth, barBottom)
            canvas.drawRect(barRect, inRangePaint)

            val textX = currentX + segmentWidth / 2f
            canvas.drawText(String.format("%d%%", displayInRange), textX, labelY, textPaint)

            currentX += segmentWidth
            isFirstSegment = false
        }

        // Draw above range bar
        if (abovePct > 0) {
            if (!isFirstSegment) currentX += separatorWidth
            val segmentWidth = (availableBarWidth * abovePct / 100f).toFloat().coerceAtLeast(1f)
            barRect.set(currentX, barTop, currentX + segmentWidth, barBottom)
            canvas.drawRect(barRect, abovePaint)

            val textX = currentX + segmentWidth / 2f
            canvas.drawText(String.format("%d%%", displayAbove), textX, labelY, textPaint)

            currentX += segmentWidth
            isFirstSegment = false
        }

        // Draw unknown (gray) bar for worst case
        if (unknownPct > 0) {
            if (!isFirstSegment) currentX += separatorWidth
            val segmentWidth = (availableBarWidth * unknownPct / 100f).toFloat().coerceAtLeast(1f)
            barRect.set(currentX, barTop, currentX + segmentWidth, barBottom)
            canvas.drawRect(barRect, unknownPaint)

            val textX = currentX + segmentWidth / 2f
            canvas.drawText(String.format("%d%%", displayUnknown), textX, labelY, textPaint)
        }
    }

    private fun drawCombinedScenarioBar(
        canvas: Canvas,
        barTop: Float,
        barHeight: Float,
        barWidth: Float,
        hPadding: Float,
        labelY: Float,
        combined: TirCombinedScenario
    ) {
        val barBottom = barTop + barHeight

        // Normalize displayed percentages to ensure they add up to 100%
        // Round to integers first
        var displayBelow = kotlin.math.round(combined.belowPct).toInt()
        var displayBestInRange = kotlin.math.round(combined.bestInRangePct).toInt()
        var displayAbove = kotlin.math.round(combined.abovePct).toInt()
        var displayWorstInRange = kotlin.math.round(combined.worstInRangePct).toInt()

        // Normalize best case (below + bestInRange + above = 100)
        val bestSum = displayBelow + displayBestInRange + displayAbove
        if (bestSum != 100) {
            val remainder = 100 - bestSum
            // Add remainder to largest value
            when {
                displayBestInRange >= displayBelow && displayBestInRange >= displayAbove -> displayBestInRange += remainder
                displayBelow >= displayAbove -> displayBelow += remainder
                else -> displayAbove += remainder
            }
        }

        // Section proportions: 1 : 1.5 : 1 (top : middle : bottom)
        // Divide by 3.5 (1 + 1.5 + 1) for top/bottom sections
        val topBottomSectionHeight = (barHeight / 3.5f)
        val middleSectionHeight = barHeight - (topBottomSectionHeight * 2)

        // Calculate positions with separators
        val nonZeroSegments = listOf(combined.belowPct > 0, true, combined.abovePct > 0).count { it }  // Middle always exists
        val totalSeparatorWidth = if (nonZeroSegments > 1) (nonZeroSegments - 1) * separatorWidth else 0f
        val availableBarWidth = barWidth - totalSeparatorWidth

        var currentX = hPadding

        // Draw below range bar (if exists)
        val belowWidth = if (combined.belowPct > 0) {
            (availableBarWidth * combined.belowPct / 100f).toFloat().coerceAtLeast(1f)
        } else 0f

        if (belowWidth > 0) {
            barRect.set(currentX, barTop, currentX + belowWidth, barBottom)
            canvas.drawRect(barRect, belowPaint)

            val textX = currentX + belowWidth / 2f
            canvas.drawText(String.format("%d%%", displayBelow), textX, labelY, textPaint)

            currentX += belowWidth + separatorWidth
        }

        // Calculate middle section width
        val aboveWidth = if (combined.abovePct > 0) {
            (availableBarWidth * combined.abovePct / 100f).toFloat().coerceAtLeast(1f)
        } else 0f
        val middleWidth = availableBarWidth - belowWidth - aboveWidth

        // Draw complex middle bar
        if (middleWidth > 0) {
            val middleLeft = currentX
            val middleRight = currentX + middleWidth

            // Calculate section boundaries
            val topY = barTop
            val topBottomY = barTop + topBottomSectionHeight
            val bottomY = barBottom - topBottomSectionHeight
            val midTopY = topBottomY
            val midBottomY = bottomY

            // Calculate green bar widths for top (worst) and bottom (best)
            val worstGreenWidth = (middleWidth * combined.worstInRangePct / combined.worstTotalMiddlePct).toFloat()
            val worstGreenLeft = middleLeft + (middleWidth - worstGreenWidth) / 2f

            // STEP 1: Draw gray background for all three sections
            barRect.set(middleLeft, topY, middleRight, barBottom)
            canvas.drawRect(barRect, unknownPaint)

            // STEP 2: Draw green elements on top with slight overlaps to avoid rounding gaps
            val overlap = 1f  // 1 pixel overlap to cover fractional pixel gaps

            // Bottom section (best case): full width green bar, extend upward slightly
            barRect.set(middleLeft, bottomY - overlap, middleRight, barBottom)
            canvas.drawRect(barRect, inRangePaint)

            // Middle section (transition): green trapezoid connecting top to bottom
            val path = android.graphics.Path()
            path.moveTo(worstGreenLeft, midTopY)  // Top left
            path.lineTo(worstGreenLeft + worstGreenWidth, midTopY)  // Top right
            path.lineTo(middleRight, midBottomY)  // Bottom right
            path.lineTo(middleLeft, midBottomY)  // Bottom left
            path.close()
            canvas.drawPath(path, inRangePaint)

            // Top section (worst case): centered green bar, extend downward slightly
            barRect.set(worstGreenLeft, topY, worstGreenLeft + worstGreenWidth, topBottomY + overlap)
            canvas.drawRect(barRect, inRangePaint)

            // Draw label with range
            val textX = middleLeft + middleWidth / 2f
            val labelText = String.format("%d%% .. %d%%", displayWorstInRange, displayBestInRange)
            canvas.drawText(labelText, textX, labelY, textPaint)

            currentX += middleWidth
        }

        // Draw above range bar (if exists)
        if (aboveWidth > 0) {
            currentX += separatorWidth
            barRect.set(currentX, barTop, currentX + aboveWidth, barBottom)
            canvas.drawRect(barRect, abovePaint)

            val textX = currentX + aboveWidth / 2f
            canvas.drawText(String.format("%d%%", displayAbove), textX, labelY, textPaint)
        }
    }

    override fun onDraw(canvas: Canvas) {

        val hPadding = dp(horizontalPadding)
        val vPadding = dp(verticalPadding)
        val tPadding = dp(titlePadding)
        val sPadding = dp(subtitlePadding)
        val rSpacing = dp(rowSpacing)
        val bHeight = dp(barHeight)
        val lSpacing = dp(labelSpacing)
        val barWidth = width - 2 * hPadding

        // Multi-scenario mode (main screen with till now + combined best/worst)
        if (chartData != null) {
            val data = chartData!!

            // Draw main title
            var currentY = vPadding + sp(titleSize)
            canvas.drawText(data.title, width / 2f, currentY, titlePaint)
            currentY += tPadding

            // Check if we should show "not enough data" message
            val notEnoughData = data.totalCount < MINIMUM_REQUIRED_DATA_COUNT ||
                    data.combinedScenario.worstInRangePct <= MINIMUM_WORST_IN_RANGE_PCT

            if (notEnoughData) {
                // Show centered "not enough data" message
                val messageY = vPadding + (height - vPadding * 2) / 2f
                val message = context.getString(R.string.tir_not_enough_data)
                canvas.drawText(message, width / 2f, messageY, textPaint)
                return
            }

            // Draw "till now" scenario
            currentY += sp(subtitleSize)
            canvas.drawText(data.tillNowScenario.subtitle, width / 2f, currentY, subtitlePaint)

            currentY += sPadding

            val tillNowBarTop = currentY
            val tillNowBarBottom = tillNowBarTop + bHeight
            val tillNowLabelY = tillNowBarBottom + lSpacing + sp(textSize)

            drawScenarioBar(
                canvas, tillNowBarTop, tillNowBarBottom, barWidth, hPadding, tillNowLabelY,
                data.tillNowScenario.belowPct, data.tillNowScenario.inRangePct, data.tillNowScenario.abovePct, 0.0
            )

            currentY = tillNowLabelY + rSpacing

            // Draw combined worst/best scenario (same height as till now)
            currentY += sp(subtitleSize)
            canvas.drawText(data.combinedScenario.subtitle, width / 2f, currentY, subtitlePaint)

            currentY += sPadding

            val combinedBarTop = currentY
            val combinedBarHeight = bHeight
            val combinedLabelY = combinedBarTop + combinedBarHeight + lSpacing + sp(textSize)

            drawCombinedScenarioBar(
                canvas, combinedBarTop, combinedBarHeight, barWidth, hPadding, combinedLabelY,
                data.combinedScenario
            )

            return
        }

        // Single row mode (history dialog)
        val tir = tirData
        if (tir == null || tir.count == 0) {
            if (title.isNotEmpty()) {
                val titleY = height / 2f
                canvas.drawText(title, width / 2f, titleY, titlePaint)
            }
            return
        }

        val belowPct = if (tir.count > 0) tir.below.toDouble() / tir.count * 100.0 else 0.0
        val inRangePct = if (tir.count > 0) tir.inRange.toDouble() / tir.count * 100.0 else 0.0
        val abovePct = if (tir.count > 0) tir.above.toDouble() / tir.count * 100.0 else 0.0

        val titleY = vPadding + sp(titleSize)
        if (title.isNotEmpty()) {
            canvas.drawText(title, width / 2f, titleY, titlePaint)
        }

        val barTop = titleY + tPadding
        val barBottom = barTop + bHeight
        val labelY = barBottom + lSpacing + sp(textSize)

        drawScenarioBar(
            canvas, barTop, barBottom, barWidth, hPadding, labelY,
            belowPct, inRangePct, abovePct, 0.0
        )
    }
}
