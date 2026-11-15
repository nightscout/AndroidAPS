package app.aaps.ui.widget.glance

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import app.aaps.core.data.model.TrendArrow
import app.aaps.ui.compose.overview.toArcIndicator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Input for [GlucoseCircleBitmapRenderer]. Texts are formatted in the user's units. */
data class GlucoseCircleInput(
    val bgText: String,
    @ColorInt val bgColor: Int,
    val strikeThrough: Boolean,
    val trendArrow: TrendArrow?,
    val hasBg: Boolean,
    val deltaText: String,
    val timeAgoText: String
)

/**
 * Renders the overview BG circle (`BgInfoSection`) to a bitmap, because Glance cannot draw arcs.
 * All sizes scale with the bitmap, in the overview's proportions.
 */
class GlucoseCircleBitmapRenderer {

    fun render(sizePx: Int, input: GlucoseCircleInput): Bitmap {
        val size = sizePx.coerceIn(MIN_PX, MAX_PX)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // One overview dp. The margin fits the arrow heads outside the ring.
        val unit = size / (CIRCLE_DP + 2 * MARGIN_DP)
        val centre = size / 2f

        if (input.hasBg) drawRing(canvas, centre, unit, input)
        drawTexts(canvas, centre, unit, input)
        return bitmap
    }

    private fun drawRing(canvas: Canvas, centre: Float, unit: Float, input: GlucoseCircleInput) {
        val stroke = RING_DP * unit
        val radius = (CIRCLE_DP * unit - stroke) / 2f
        val oval = RectF(centre - radius, centre - radius, centre + radius, centre + radius)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
            color = ColorUtils.setAlphaComponent(input.bgColor, RING_ALPHA)
        }
        canvas.drawArc(oval, 0f, 360f, false, ring)

        val indicator = input.trendArrow?.toArcIndicator() ?: return
        ring.color = input.bgColor
        canvas.drawArc(oval, indicator.centerAngle - indicator.sweepAngle / 2, indicator.sweepAngle, false, ring)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = input.bgColor
        }
        val triHeight = stroke * 1.2f
        val triHalfBase = stroke * 1.6f
        val baseDist = radius + stroke * 0.2f
        val spacing = (triHalfBase * 1.6 / radius * 180.0 / PI).toFloat()
        val n = indicator.triangleCount
        for (i in 0 until n) {
            val angle = (indicator.centerAngle + (i - (n - 1) / 2f) * spacing) / 180.0 * PI
            val dirX = cos(angle).toFloat()
            val dirY = sin(angle).toFloat()
            val baseX = centre + baseDist * dirX
            val baseY = centre + baseDist * dirY
            val path = Path().apply {
                moveTo(baseX + triHeight * dirX, baseY + triHeight * dirY)
                lineTo(baseX - triHalfBase * dirY, baseY + triHalfBase * dirX)
                lineTo(baseX + triHalfBase * dirY, baseY - triHalfBase * dirX)
                close()
            }
            canvas.drawPath(path, fill)
        }
    }

    private fun drawTexts(canvas: Canvas, centre: Float, unit: Float, input: GlucoseCircleInput) {
        val value = textPaint(VALUE_SP * unit, input.bgColor, bold = true).apply { isStrikeThruText = input.strikeThrough }
        if (!input.hasBg) {
            canvas.drawText(input.bgText, centre, centre - (value.ascent() + value.descent()) / 2, value.also { it.color = MUTED })
            return
        }
        val delta = textPaint(SMALL_SP * unit, MUTED, bold = true)
        val time = textPaint(SMALL_SP * unit, MUTED, bold = false)
        val lines = listOf(input.deltaText to delta, input.bgText to value, input.timeAgoText to time).filter { it.first.isNotEmpty() }
        // Matches the overview's negative line spacing and bottom padding.
        val gap = LINE_GAP_DP * unit
        val heights = lines.map { (_, paint) -> paint.descent() - paint.ascent() }
        var top = centre - (heights.sum() + gap * (lines.size - 1)) / 2f - LIFT_DP * unit
        lines.forEachIndexed { i, (text, paint) ->
            canvas.drawText(text, centre, top - paint.ascent(), paint)
            top += heights[i] + gap
        }
    }

    private fun textPaint(sizePx: Float, @ColorInt colour: Int, bold: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sizePx
        color = colour
        textAlign = Paint.Align.CENTER
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    companion object {

        const val CIRCLE_DP = 126f
        const val MARGIN_DP = 12f
        const val RING_DP = 8f
        const val VALUE_SP = 50f
        const val SMALL_SP = 17f
        const val LINE_GAP_DP = -4f
        const val LIFT_DP = 2f

        const val RING_ALPHA = (0.3f * 255).toInt()

        /** Dark theme onSurfaceVariant. */
        @ColorInt const val MUTED = 0xFFCAC4D0.toInt()

        /** Keeps the bitmap within the RemoteViews size limit. */
        const val MAX_PX = 1024
        const val MIN_PX = 48
    }
}
