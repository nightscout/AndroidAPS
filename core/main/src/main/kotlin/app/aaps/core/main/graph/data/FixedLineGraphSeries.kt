package app.aaps.core.main.graph.data

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.BaseSeries
import com.jjoe64.graphview.series.DataPointInterface

/**
 * Series to plot the data as line.
 * The line can be styled with many options.
 *
 * @author jjoe64
 */
@Suppress("unused") class FixedLineGraphSeries<E : DataPointInterface?> : BaseSeries<E> {

    /**
     * wrapped styles regarding the line
     */
    private class Styles {

        /**
         * the thickness of the line.
         * This option will be ignored if you are
         * using a custom paint via [.setCustomPaint]
         */
        var thickness = 5

        /**
         * flag whether the area under the line to the bottom
         * of the viewport will be filled with a
         * specific background color.
         *
         * @see .backgroundColor
         */
        var drawBackground = false

        /**
         * flag whether the data points are highlighted as
         * a visible point.
         *
         * @see .dataPointsRadius
         */
        var drawDataPoints = false

        /**
         * the radius for the data points.
         *
         * @see .drawDataPoints
         */
        var dataPointsRadius = 10f

        /**
         * the background color for the filling under
         * the line.
         *
         * @see .drawBackground
         */
        var backgroundColor = Color.argb(100, 172, 218, 255)
    }

    /**
     * wrapped styles
     */
    private lateinit var mStyles: Styles

    /**
     * internal paint object
     */
    private lateinit var mPaint: Paint

    /**
     * paint for the background
     */
    private lateinit var mPaintBackground: Paint

    /**
     * path for the background filling
     */
    private lateinit var mPathBackground: Path

    /**
     * path to the line
     */
    private lateinit var mPath: Path

    /**
     * custom paint that can be used.
     * this will ignore the thickness and color styles.
     */
    private var mCustomPaint: Paint? = null

    /**
     * creates a series without data
     */
    @Suppress("unused")
    constructor() {
        init()
    }

    /**
     * creates a series with data
     *
     * @param data data points
     */
    constructor(data: Array<E>?) : super(data) {
        init()
    }

    /**
     * do the initialization
     * creates internal objects
     */
    private fun init() {
        mStyles = Styles()
        mPaint = Paint()
        mPaint.strokeCap = Paint.Cap.ROUND
        mPaint.style = Paint.Style.STROKE
        mPaintBackground = Paint()
        mPathBackground = Path()
        mPath = Path()
    }

    /**
     * plots the series
     * draws the line and the background
     *
     * @param graphView graphview
     * @param canvas canvas
     * @param isSecondScale flag if it is the second scale
     */
    override fun draw(graphView: GraphView, canvas: Canvas, isSecondScale: Boolean) {
        resetDataPoints()

        // get data
        val maxX = graphView.viewport.getMaxX(false)
        val minX = graphView.viewport.getMinX(false)
        val maxY: Double
        val minY: Double
        if (isSecondScale) {
            maxY = graphView.secondScale.maxY
            minY = graphView.secondScale.minY
        } else {
            maxY = graphView.viewport.getMaxY(false)
            minY = graphView.viewport.getMinY(false)
        }
        val values = getValues(minX, maxX)

        // draw background
        var lastEndY: Double
        var lastEndX: Double

        // draw data
        mPaint.strokeWidth = mStyles.thickness.toFloat()
        mPaint.color = color
        mPaintBackground.color = mStyles.backgroundColor
        val paint = mCustomPaint ?: mPaint
        if (mStyles.drawBackground) {
            mPathBackground.reset()
        }
        val diffY = maxY - minY
        val diffX = maxX - minX
        val graphHeight = graphView.graphContentHeight.toFloat()
        val graphWidth = graphView.graphContentWidth.toFloat()
        val graphLeft = graphView.graphContentLeft.toFloat()
        val graphTop = graphView.graphContentTop.toFloat()
        lastEndY = 0.0
        lastEndX = 0.0
        var lastUsedEndX = 0.0
        var firstX = 0f
        var i = 0
        while (values.hasNext()) {
            val value = values.next() ?: break
            val valY = value.y - minY
            val ratY = valY / diffY
            var y = graphHeight * ratY
            val valX = value.x - minX
            val ratX = valX / diffX
            var x = graphWidth * ratX
            val orgX = x
            val orgY = y
            @Suppress("ControlFlowWithEmptyBody")
            if (i > 0) {
                // overdraw
                if (x > graphWidth) { // end right
                    val b = (graphWidth - lastEndX) * (y - lastEndY) / (x - lastEndX)
                    y = lastEndY + b
                    x = graphWidth.toDouble()
                }
                if (y < 0) { // end bottom
                    val b = (0 - lastEndY) * (x - lastEndX) / (y - lastEndY)
                    x = lastEndX + b
                    y = 0.0
                }
                if (y > graphHeight) { // end top
                    val b = (graphHeight - lastEndY) * (x - lastEndX) / (y - lastEndY)
                    x = lastEndX + b
                    y = graphHeight.toDouble()
                }
                if (lastEndY < 0) { // start bottom
                    val b = (0 - y) * (x - lastEndX) / (lastEndY - y)
                    lastEndX = x - b
                    lastEndY = 0.0
                }
                if (lastEndX < 0) { // start left
                    val b = (0 - x) * (y - lastEndY) / (lastEndX - x)
                    lastEndY = y - b
                    lastEndX = 0.0
                }
                if (lastEndY > graphHeight) { // start top
                    val b = (graphHeight - y) * (x - lastEndX) / (lastEndY - y)
                    lastEndX = x - b
                    lastEndY = graphHeight.toDouble()
                }
                val startX = lastEndX.toFloat() + (graphLeft + 1)
                val startY = (graphTop - lastEndY).toFloat() + graphHeight
                val endX = x.toFloat() + (graphLeft + 1)
                val endY = (graphTop - y).toFloat() + graphHeight

                // draw data point
                if (mStyles.drawDataPoints) {
                    //fix: last value was not drawn. Draw here now the end values
                    canvas.drawCircle(endX, endY, mStyles.dataPointsRadius, mPaint)
                }
                registerDataPoint(endX, endY, value)
                mPath.reset()
                mPath.moveTo(startX, startY)
                mPath.lineTo(endX, endY)
                canvas.drawPath(mPath, paint)
                if (mStyles.drawBackground) {
                    if (i == 1) {
                        firstX = startX
                        mPathBackground.moveTo(startX, startY)
                    }
                    mPathBackground.lineTo(endX, endY)
                }
                lastUsedEndX = endX.toDouble()
            } else if (mStyles.drawDataPoints) {
                //fix: last value not drawn as datapoint. Draw first point here, and then on every step the end values (above)
                // float first_X = (float) x + (graphLeft + 1);
                // float first_Y = (float) (graphTop - y) + graphHeight;
                // canvas.drawCircle(first_X, first_Y, dataPointsRadius, mPaint);
            }
            lastEndY = orgY
            lastEndX = orgX
            i++
        }
        if (mStyles.drawBackground) {
            // end / close path
            mPathBackground.lineTo(lastUsedEndX.toFloat(), (graphTop - -minY / diffY * graphHeight).toFloat() + graphHeight)
            mPathBackground.lineTo(firstX, (graphTop - -minY / diffY * graphHeight).toFloat() + graphHeight)
            mPathBackground.close()
            canvas.drawPath(mPathBackground, mPaintBackground)
        }
    }

    var thickness: Int
        /**
         * the thickness of the line.
         * This option will be ignored if you are
         * using a custom paint via [.setCustomPaint]
         *
         * @return the thickness of the line
         */
        get() = mStyles.thickness
        /**
         * the thickness of the line.
         * This option will be ignored if you are
         * using a custom paint via [.setCustomPaint]
         *
         * @param thickness thickness of the line
         */
        set(thickness) {
            mStyles.thickness = thickness
        }
    var isDrawBackground: Boolean
        /**
         * flag whether the area under the line to the bottom
         * of the viewport will be filled with a
         * specific background color.
         *
         * @return whether the background will be drawn
         * @see .getBackgroundColor
         */
        get() = mStyles.drawBackground
        /**
         * flag whether the area under the line to the bottom
         * of the viewport will be filled with a
         * specific background color.
         *
         * @param drawBackground whether the background will be drawn
         * @see .setBackgroundColor
         */
        set(drawBackground) {
            mStyles.drawBackground = drawBackground
        }
    var isDrawDataPoints: Boolean
        /**
         * flag whether the data points are highlighted as
         * a visible point.
         *
         * @return flag whether the data points are highlighted
         * @see .setDataPointsRadius
         */
        get() = mStyles.drawDataPoints
        /**
         * flag whether the data points are highlighted as
         * a visible point.
         *
         * @param drawDataPoints flag whether the data points are highlighted
         * @see .setDataPointsRadius
         */
        set(drawDataPoints) {
            mStyles.drawDataPoints = drawDataPoints
        }
    var dataPointsRadius: Float
        /**
         * @return the radius for the data points.
         * @see .setDrawDataPoints
         */
        get() = mStyles.dataPointsRadius
        /**
         * @param dataPointsRadius the radius for the data points.
         * @see .setDrawDataPoints
         */
        set(dataPointsRadius) {
            mStyles.dataPointsRadius = dataPointsRadius
        }
    var backgroundColor: Int
        /**
         * @return  the background color for the filling under
         * the line.
         * @see .setDrawBackground
         */
        get() = mStyles.backgroundColor
        /**
         * @param backgroundColor  the background color for the filling under
         * the line.
         * @see .setDrawBackground
         */
        set(backgroundColor) {
            mStyles.backgroundColor = backgroundColor
        }

    /**
     * custom paint that can be used.
     * this will ignore the thickness and color styles.
     *
     * @param customPaint the custom paint to be used for rendering the line
     */
    fun setCustomPaint(customPaint: Paint?) {
        mCustomPaint = customPaint
    }
}
