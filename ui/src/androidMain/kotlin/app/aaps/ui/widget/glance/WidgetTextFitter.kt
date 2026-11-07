package app.aaps.ui.widget.glance

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue

/**
 * Finds a font size at which a text fits its place on the widget. Glance has no auto-size text, so
 * the widget measures the text itself - with the bold system font and the user's font scale, the same
 * ones the launcher draws with - and gives Glance a size that fits. Before, a long profile name or a
 * large font scale was simply cut off (#5340).
 *
 * The place comes from the widget size the launcher reports. A launcher can draw a little smaller than
 * it reports, so a small margin is kept on the width.
 */
internal class WidgetTextFitter(context: Context) {

    private val metrics = context.resources.displayMetrics
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }

    fun dpToPx(dp: Float): Float = dp * metrics.density

    /** The width a text may use in [dp], margin already taken off. */
    fun widthLimitPx(dp: Float): Float = dpToPx(dp) * WIDTH_MARGIN

    /** Width of the widest line of [text] at [sp], in px. */
    fun widthPx(text: String, sp: Float): Float {
        if (text.isEmpty()) return 0f
        setSize(sp)
        return text.lines().maxOf { paint.measureText(it) }
    }

    /** Height of [lines] lines at [sp] as a TextView draws them, font padding included, in px. */
    fun heightPx(sp: Float, lines: Int = 1): Float {
        setSize(sp)
        val fm = paint.fontMetrics
        return fm.bottom - fm.top + (lines - 1) * (fm.descent - fm.ascent)
    }

    /** The largest size from [maxSp] down to [minSp] for which [fits] is true; [minSp] when none is. */
    fun largestFitting(maxSp: Float, minSp: Float, fits: (sp: Float) -> Boolean): Float {
        var sp = maxSp
        while (sp >= minSp) {
            if (fits(sp)) return sp
            sp -= STEP_SP
        }
        return minSp
    }

    /**
     * Like [largestFitting] for a text that may wrap onto up to [maxLines] lines. A larger size on two
     * lines wins over a smaller size on one. Also returns how many lines to allow, so the text is never
     * given more lines than [heightDp] can show.
     */
    fun fitWrapping(text: String, widthDp: Float, heightDp: Float, maxSp: Float, minSp: Float, maxLines: Int): TextFit {
        val width = widthLimitPx(widthDp).toInt()
        val height = dpToPx(heightDp)
        if (text.isEmpty()) return TextFit(maxSp, 1)
        if (width <= 0) return TextFit(minSp, 1)
        var sp = maxSp
        while (sp >= minSp) {
            val lines = lineCount(text, sp, width)
            if (lines <= maxLines && heightPx(sp, lines) <= height) return TextFit(sp, lines)
            sp -= STEP_SP
        }
        return TextFit(minSp, (1..maxLines).lastOrNull { heightPx(minSp, it) <= height } ?: 1)
    }

    /** Lines a TextView needs for [text] at [sp] in [widthPx]. A word broken in two does not fit at all. */
    private fun lineCount(text: String, sp: Float, widthPx: Int): Int {
        setSize(sp)
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx)
            .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
            .build()
        for (line in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(line)
            if (text[end - 1].isLetterOrDigit() && text[end].isLetterOrDigit()) return Int.MAX_VALUE
        }
        return layout.lineCount
    }

    // applyDimension, not sp * density: since Android 14 large font scales do not grow linearly.
    private fun setSize(sp: Float) {
        paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, metrics)
    }

    private companion object {

        const val STEP_SP = 0.5f

        /** Room for a launcher that draws a little smaller than it reports. */
        const val WIDTH_MARGIN = 0.95f
    }
}

/** A font size and the number of lines the text may use at that size. */
internal data class TextFit(val sp: Float, val maxLines: Int)
