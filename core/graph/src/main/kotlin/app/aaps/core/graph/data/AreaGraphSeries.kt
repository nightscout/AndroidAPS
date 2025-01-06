/**
 * GraphView
 * Copyright (C) 2014  Jonas Gehring
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License,
 * with the "Linking Exception", which can be found at the license.txt
 * file in this program.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * with the "Linking Exception" along with this program; if not,
 * write to the author Jonas Gehring <g.jjoe64></g.jjoe64>@gmail.com>.
 */
package app.aaps.core.graph.data

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import app.aaps.core.interfaces.graph.SeriesData
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.BaseSeries

/**
 * Series to plot the data as line.
 * The line can be styled with many options.
 *
 * @author jjoe64
 */
@Suppress("unused") class AreaGraphSeries<E : DoubleDataPoint?> : BaseSeries<E>, SeriesData {

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
    private lateinit var mSecondPath: Path

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
        mSecondPath = Path()
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
        var lastEndY1: Double
        var lastEndY2: Double
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
        lastEndY1 = 0.0
        lastEndY2 = 0.0
        lastEndX = 0.0
        var i = 0
        while (values.hasNext()) {
            val value = values.next() ?: break
            val valY1 = value.y - minY
            val ratY1 = valY1 / diffY
            var y1 = graphHeight * ratY1
            val valY2 = value.y2 - minY
            val ratY2 = valY2 / diffY
            var y2 = graphHeight * ratY2
            val valX = value.x - minX
            val ratX = valX / diffX
            var x = graphWidth * ratX
            val orgX = x
            val orgY1 = y1
            val orgY2 = y2
            @Suppress("ControlFlowWithEmptyBody")
            if (i > 0) {
                // overdraw
                if (x > graphWidth) { // end right
                    val b = (graphWidth - lastEndX) * (y1 - lastEndY1) / (x - lastEndX)
                    y1 = lastEndY1 + b
                    x = graphWidth.toDouble()
                }
                if (x > graphWidth) { // end right
                    val b = (graphWidth - lastEndX) * (y2 - lastEndY2) / (x - lastEndX)
                    y2 = lastEndY2 + b
                    x = graphWidth.toDouble()
                }
                if (y1 < 0) { // end bottom
                    val b = (0 - lastEndY1) * (x - lastEndX) / (y1 - lastEndY1)
                    x = lastEndX + b
                    y1 = 0.0
                }
                if (y2 < 0) { // end bottom
                    val b = (0 - lastEndY2) * (x - lastEndX) / (y2 - lastEndY2)
                    x = lastEndX + b
                    y2 = 0.0
                }
                if (y1 > graphHeight) { // end top
                    val b = (graphHeight - lastEndY1) * (x - lastEndX) / (y1 - lastEndY1)
                    x = lastEndX + b
                    y1 = graphHeight.toDouble()
                }
                if (y2 > graphHeight) { // end top
                    val b = (graphHeight - lastEndY2) * (x - lastEndX) / (y2 - lastEndY2)
                    x = lastEndX + b
                    y2 = graphHeight.toDouble()
                }
                if (lastEndY1 < 0) { // start bottom
                    val b = (0 - y1) * (x - lastEndX) / (lastEndY1 - y1)
                    lastEndX = x - b
                    lastEndY1 = 0.0
                }
                if (lastEndY2 < 0) { // start bottom
                    val b = (0 - y2) * (x - lastEndX) / (lastEndY2 - y2)
                    lastEndX = x - b
                    lastEndY2 = 0.0
                }
                if (lastEndX < 0) { // start left
                    val b = (0 - x) * (y1 - lastEndY1) / (lastEndX - x)
                    lastEndY1 = y1 - b
                    lastEndX = 0.0
                }
                if (lastEndY1 > graphHeight) { // start top
                    val b = (graphHeight - y1) * (x - lastEndX) / (lastEndY1 - y1)
                    lastEndX = x - b
                    lastEndY1 = graphHeight.toDouble()
                }
                if (lastEndY2 > graphHeight) { // start top
                    val b = (graphHeight - y2) * (x - lastEndX) / (lastEndY2 - y2)
                    lastEndX = x - b
                    lastEndY2 = graphHeight.toDouble()
                }
                val startX = lastEndX.toFloat() + (graphLeft + 1)
                val startY1 = (graphTop - lastEndY1).toFloat() + graphHeight
                val startY2 = (graphTop - lastEndY2).toFloat() + graphHeight
                val endX = x.toFloat() + (graphLeft + 1)
                val endY1 = (graphTop - y1).toFloat() + graphHeight
                val endY2 = (graphTop - y2).toFloat() + graphHeight

                // draw data point
                if (mStyles.drawDataPoints) {
                    //fix: last value was not drawn. Draw here now the end values
                    canvas.drawCircle(endX, endY1, mStyles.dataPointsRadius, mPaint)
                    canvas.drawCircle(endX, endY2, mStyles.dataPointsRadius, mPaint)
                }
                registerDataPoint(endX, endY1, value)
                registerDataPoint(endX, endY2, value)
                mPath.reset()
                mSecondPath.reset()
                mPath.moveTo(startX, startY1)
                mSecondPath.moveTo(startX, startY2)
                mPath.lineTo(endX, endY1)
                mSecondPath.lineTo(endX, endY2)
                canvas.drawPath(mPath, paint)
                canvas.drawPath(mSecondPath, paint)
                if (mStyles.drawBackground) {
                    canvas.drawRect(startX, startY2, endX, endY1, mPaintBackground)
                }
            } else if (mStyles.drawDataPoints) {
                //fix: last value not drawn as datapoint. Draw first point here, and then on every step the end values (above)
                //float first_X = (float) x + (graphLeft + 1);
                //float first_Y = (float) (graphTop - y) + graphHeight;
                // canvas.drawCircle(first_X, first_Y, dataPointsRadius, mPaint);
            }
            lastEndY1 = orgY1
            lastEndY2 = orgY2
            lastEndX = orgX
            i++
        }

        /*
        if (mStyles.drawBackground) {
            // end / close path
            mPathBackground.lineTo((float) lastUsedEndX, graphHeight + graphTop);
            mPathBackground.lineTo(firstX, graphHeight + graphTop);
            mPathBackground.close();
            canvas.drawPath(mPathBackground, mPaintBackground);
        }
*/
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
