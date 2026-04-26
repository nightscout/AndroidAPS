package app.aaps.ui.widget.glance

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.BgRange
import app.aaps.core.interfaces.overview.graph.BgType

/**
 * Inputs for [BgGraphBitmapRenderer]. All BG values are in user units
 * (mg/dL or mmol/L), matching [BgDataPoint.value].
 */
data class BgGraphInput(
    val bucketed: List<BgDataPoint>,
    val fromTimeMs: Long,
    val toTimeMs: Long,
    val yMinUserUnits: Double,
    val yMaxUserUnits: Double
)

data class BgGraphColors(
    val veryLow: Int,
    val low: Int,
    val inRange: Int,
    val high: Int,
    val veryHigh: Int
)

/**
 * Minimal Canvas BG-graph renderer producing a transparent bitmap.
 * Draws only bucketed BG dots colored by [BgRange].
 */
class BgGraphBitmapRenderer {

    fun render(widthPx: Int, heightPx: Int, input: BgGraphInput, colors: BgGraphColors): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val left = PADDING_PX
        val right = widthPx - PADDING_PX
        val top = PADDING_PX
        val bottom = heightPx - PADDING_PX
        if (right <= left || bottom <= top) return bitmap
        if (input.bucketed.isEmpty()) return bitmap

        val xSpan = (input.toTimeMs - input.fromTimeMs).coerceAtLeast(1L).toFloat()
        val ySpan = (input.yMaxUserUnits - input.yMinUserUnits).coerceAtLeast(0.001).toFloat()

        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        for (p in input.bucketed) {
            if (p.type != BgType.BUCKETED) continue
            if (p.timestamp !in input.fromTimeMs..input.toTimeMs) continue
            val x = left + (p.timestamp - input.fromTimeMs).toFloat() / xSpan * (right - left)
            val y = bottom - ((p.value - input.yMinUserUnits).toFloat() / ySpan) * (bottom - top)
            paint.color = colorForRange(p.range, colors)
            paint.alpha = if (p.filledGap) GAP_ALPHA else 255
            canvas.drawCircle(x, y, BG_RADIUS_PX, paint)
        }

        return bitmap
    }

    private fun colorForRange(range: BgRange, colors: BgGraphColors): Int = when (range) {
        BgRange.VERY_LOW  -> colors.veryLow
        BgRange.LOW       -> colors.low
        BgRange.IN_RANGE  -> colors.inRange
        BgRange.HIGH      -> colors.high
        BgRange.VERY_HIGH -> colors.veryHigh
    }

    companion object {

        private const val PADDING_PX = 4
        private const val BG_RADIUS_PX = 6.0f
        private const val GAP_ALPHA = 110
    }
}
