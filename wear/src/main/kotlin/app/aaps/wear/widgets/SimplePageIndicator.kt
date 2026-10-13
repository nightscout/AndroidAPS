package app.aaps.wear.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import app.aaps.wear.R

/**
 * Simple page indicator for horizontal paging with WearableRecyclerView.
 * Displays dots to indicate current page position.
 */
class SimplePageIndicator @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var pageCount = 0
    private var currentPage = 0

    private val dotRadius = 4f * resources.displayMetrics.density
    private val dotSpacing = 12f * resources.displayMetrics.density
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectedDotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        dotPaint.color = ContextCompat.getColor(context, R.color.light_grey)
        dotPaint.style = Paint.Style.FILL

        selectedDotPaint.color = ContextCompat.getColor(context, android.R.color.white)
        selectedDotPaint.style = Paint.Style.FILL
    }

    fun setPageCount(count: Int) {
        pageCount = count
        requestLayout()
        invalidate()
    }

    fun setCurrentPage(page: Int) {
        if (currentPage != page) {
            currentPage = page
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = ((pageCount * dotRadius * 2) + ((pageCount - 1) * dotSpacing)).toInt()
        val height = (dotRadius * 2).toInt()
        setMeasuredDimension(width, height + paddingTop + paddingBottom)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (pageCount == 0) return

        val centerY = height / 2f
        var centerX = dotRadius + paddingLeft

        for (i in 0 until pageCount) {
            val paint = if (i == currentPage) selectedDotPaint else dotPaint
            canvas.drawCircle(centerX, centerY, dotRadius, paint)
            centerX += (dotRadius * 2 + dotSpacing)
        }
    }
}
