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
 * write to the author Jonas Gehring <g.jjoe64@gmail.com>.
 */
package info.nightscout.androidaps.plugins.general.overview.graphExtensions;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.BaseSeries;

import java.util.Iterator;

/**
 * Series to plot the data as line.
 * The line can be styled with many options.
 *
 * @author jjoe64
 */
public class AreaGraphSeries<E extends DoubleDataPoint> extends BaseSeries<E> {
    /**
     * wrapped styles regarding the line
     */
    private final class Styles {
        /**
         * the thickness of the line.
         * This option will be ignored if you are
         * using a custom paint via {@link #setCustomPaint(android.graphics.Paint)}
         */
        private int thickness = 5;

        /**
         * flag whether the area under the line to the bottom
         * of the viewport will be filled with a
         * specific background color.
         *
         * @see #backgroundColor
         */
        private boolean drawBackground = false;

        /**
         * flag whether the data points are highlighted as
         * a visible point.
         *
         * @see #dataPointsRadius
         */
        private boolean drawDataPoints = false;

        /**
         * the radius for the data points.
         *
         * @see #drawDataPoints
         */
        private float dataPointsRadius = 10f;

        /**
         * the background color for the filling under
         * the line.
         *
         * @see #drawBackground
         */
        private int backgroundColor = Color.argb(100, 172, 218, 255);
    }

    /**
     * wrapped styles
     */
    private Styles mStyles;

    /**
     * internal paint object
     */
    private Paint mPaint;

    /**
     * paint for the background
     */
    private Paint mPaintBackground;

    /**
     * path for the background filling
     */
    private Path mPathBackground;

    /**
     * path to the line
     */
    private Path mPath;
    private Path mSecondPath;

    /**
     * custom paint that can be used.
     * this will ignore the thickness and color styles.
     */
    private Paint mCustomPaint;

    /**
     * creates a series without data
     */
    public AreaGraphSeries() {
        init();
    }

    /**
     * creates a series with data
     *
     * @param data data points
     */
    public AreaGraphSeries(E[] data) {
        super(data);
        init();
    }

    /**
     * do the initialization
     * creates internal objects
     */
    protected void init() {
        mStyles = new Styles();
        mPaint = new Paint();
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaintBackground = new Paint();

        mPathBackground = new Path();
        mPath = new Path();
        mSecondPath = new Path();
    }

    /**
     * plots the series
     * draws the line and the background
     *
     * @param graphView graphview
     * @param canvas canvas
     * @param isSecondScale flag if it is the second scale
     */
    @Override
    public void draw(GraphView graphView, Canvas canvas, boolean isSecondScale) {
        resetDataPoints();

        // get data
        double maxX = graphView.getViewport().getMaxX(false);
        double minX = graphView.getViewport().getMinX(false);

        double maxY;
        double minY;
        if (isSecondScale) {
            maxY = graphView.getSecondScale().getMaxY();
            minY = graphView.getSecondScale().getMinY();
        } else {
            maxY = graphView.getViewport().getMaxY(false);
            minY = graphView.getViewport().getMinY(false);
        }

        Iterator<E> values = getValues(minX, maxX);

        // draw background
        double lastEndY1 = 0;
        double lastEndY2 = 0;
        double lastEndX = 0;

        // draw data
        mPaint.setStrokeWidth(mStyles.thickness);
        mPaint.setColor(getColor());
        mPaintBackground.setColor(mStyles.backgroundColor);

        Paint paint;
        if (mCustomPaint != null) {
            paint = mCustomPaint;
        } else {
            paint = mPaint;
        }

        if (mStyles.drawBackground) {
            mPathBackground.reset();
        }

        double diffY = maxY - minY;
        double diffX = maxX - minX;

        float graphHeight = graphView.getGraphContentHeight();
        float graphWidth = graphView.getGraphContentWidth();
        float graphLeft = graphView.getGraphContentLeft();
        float graphTop = graphView.getGraphContentTop();

        lastEndY1 = 0;
        lastEndY2 = 0;
        lastEndX = 0;
        double lastUsedEndX = 0;
        float firstX = 0;
        int i=0;
        while (values.hasNext()) {
            E value = values.next();

            double valY1 = value.getY() - minY;
            double ratY1 = valY1 / diffY;
            double y1 = graphHeight * ratY1;

            double valY2 = value.getY2() - minY;
            double ratY2 = valY2 / diffY;
            double y2 = graphHeight * ratY2;

            double valX = value.getX() - minX;
            double ratX = valX / diffX;
            double x = graphWidth * ratX;

            double orgX = x;
            double orgY1 = y1;
            double orgY2 = y2;

            if (i > 0) {
                // overdraw
                if (x > graphWidth) { // end right
                    double b = ((graphWidth - lastEndX) * (y1 - lastEndY1)/(x - lastEndX));
                    y1 = lastEndY1+b;
                    x = graphWidth;
                }
                if (x > graphWidth) { // end right
                    double b = ((graphWidth - lastEndX) * (y2 - lastEndY2)/(x - lastEndX));
                    y2 = lastEndY2+b;
                    x = graphWidth;
                }
                if (y1 < 0) { // end bottom
                    double b = ((0 - lastEndY1) * (x - lastEndX)/(y1 - lastEndY1));
                    x = lastEndX+b;
                    y1 = 0;
                }
                if (y2 < 0) { // end bottom
                    double b = ((0 - lastEndY2) * (x - lastEndX)/(y2 - lastEndY2));
                    x = lastEndX+b;
                    y2 = 0;
                }
                if (y1 > graphHeight) { // end top
                    double b = ((graphHeight - lastEndY1) * (x - lastEndX)/(y1 - lastEndY1));
                    x = lastEndX+b;
                    y1 = graphHeight;
                }
                if (y2 > graphHeight) { // end top
                    double b = ((graphHeight - lastEndY2) * (x - lastEndX)/(y2 - lastEndY2));
                    x = lastEndX+b;
                    y2 = graphHeight;
                }
                if (lastEndY1 < 0) { // start bottom
                    double b = ((0 - y1) * (x - lastEndX)/(lastEndY1 - y1));
                    lastEndX = x-b;
                    lastEndY1 = 0;
                }
                if (lastEndY2 < 0) { // start bottom
                    double b = ((0 - y2) * (x - lastEndX)/(lastEndY2 - y2));
                    lastEndX = x-b;
                    lastEndY2 = 0;
                }
                if (lastEndX < 0) { // start left
                    double b = ((0 - x) * (y1 - lastEndY1)/(lastEndX - x));
                    lastEndY1 = y1-b;
                    lastEndX = 0;
                }
                if (lastEndX < 0) { // start left
                    double b = ((0 - x) * (y2 - lastEndY2)/(lastEndX - x));
                    lastEndY2 = y2-b;
                    lastEndX = 0;
                }
                if (lastEndY1 > graphHeight) { // start top
                    double b = ((graphHeight - y1) * (x - lastEndX)/(lastEndY1 - y1));
                    lastEndX = x-b;
                    lastEndY1 = graphHeight;
                }
                if (lastEndY2 > graphHeight) { // start top
                    double b = ((graphHeight - y2) * (x - lastEndX)/(lastEndY2 - y2));
                    lastEndX = x-b;
                    lastEndY2 = graphHeight;
                }

                float startX = (float) lastEndX + (graphLeft + 1);
                float startY1 = (float) (graphTop - lastEndY1) + graphHeight;
                float startY2 = (float) (graphTop - lastEndY2) + graphHeight;
                float endX = (float) x + (graphLeft + 1);
                float endY1 = (float) (graphTop - y1) + graphHeight;
                float endY2 = (float) (graphTop - y2) + graphHeight;

                // draw data point
                if (mStyles.drawDataPoints) {
                    //fix: last value was not drawn. Draw here now the end values
                    canvas.drawCircle(endX, endY1, mStyles.dataPointsRadius, mPaint);
                    canvas.drawCircle(endX, endY2, mStyles.dataPointsRadius, mPaint);
                }
                registerDataPoint(endX, endY1, value);
                registerDataPoint(endX, endY2, value);

                mPath.reset();
                mSecondPath.reset();
                mPath.moveTo(startX, startY1);
                mSecondPath.moveTo(startX, startY2);
                mPath.lineTo(endX, endY1);
                mSecondPath.lineTo(endX, endY2);
                canvas.drawPath(mPath, paint);
                canvas.drawPath(mSecondPath, paint);
                if (mStyles.drawBackground) {
                    canvas.drawRect((float)startX, (float)startY2, endX, endY1, mPaintBackground);
                }
            } else if (mStyles.drawDataPoints) {
                //fix: last value not drawn as datapoint. Draw first point here, and then on every step the end values (above)
                //float first_X = (float) x + (graphLeft + 1);
                //float first_Y = (float) (graphTop - y) + graphHeight;
                //TODO canvas.drawCircle(first_X, first_Y, dataPointsRadius, mPaint);
            }
            lastEndY1 = orgY1;
            lastEndY2 = orgY2;
            lastEndX = orgX;
            i++;
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

    /**
     * the thickness of the line.
     * This option will be ignored if you are
     * using a custom paint via {@link #setCustomPaint(android.graphics.Paint)}
     *
     * @return the thickness of the line
     */
    public int getThickness() {
        return mStyles.thickness;
    }

    /**
     * the thickness of the line.
     * This option will be ignored if you are
     * using a custom paint via {@link #setCustomPaint(android.graphics.Paint)}
     *
     * @param thickness thickness of the line
     */
    public void setThickness(int thickness) {
        mStyles.thickness = thickness;
    }

    /**
     * flag whether the area under the line to the bottom
     * of the viewport will be filled with a
     * specific background color.
     *
     * @return whether the background will be drawn
     * @see #getBackgroundColor()
     */
    public boolean isDrawBackground() {
        return mStyles.drawBackground;
    }

    /**
     * flag whether the area under the line to the bottom
     * of the viewport will be filled with a
     * specific background color.
     *
     * @param drawBackground whether the background will be drawn
     * @see #setBackgroundColor(int)
     */
    public void setDrawBackground(boolean drawBackground) {
        mStyles.drawBackground = drawBackground;
    }

    /**
     * flag whether the data points are highlighted as
     * a visible point.
     *
     * @return flag whether the data points are highlighted
     * @see #setDataPointsRadius(float)
     */
    public boolean isDrawDataPoints() {
        return mStyles.drawDataPoints;
    }

    /**
     * flag whether the data points are highlighted as
     * a visible point.
     *
     * @param drawDataPoints flag whether the data points are highlighted
     * @see #setDataPointsRadius(float)
     */
    public void setDrawDataPoints(boolean drawDataPoints) {
        mStyles.drawDataPoints = drawDataPoints;
    }

    /**
     * @return the radius for the data points.
     * @see #setDrawDataPoints(boolean)
     */
    public float getDataPointsRadius() {
        return mStyles.dataPointsRadius;
    }

    /**
     * @param dataPointsRadius the radius for the data points.
     * @see #setDrawDataPoints(boolean)
     */
    public void setDataPointsRadius(float dataPointsRadius) {
        mStyles.dataPointsRadius = dataPointsRadius;
    }

    /**
     * @return  the background color for the filling under
     *          the line.
     * @see #setDrawBackground(boolean)
     */
    public int getBackgroundColor() {
        return mStyles.backgroundColor;
    }

    /**
     * @param backgroundColor  the background color for the filling under
     *                          the line.
     * @see #setDrawBackground(boolean)
     */
    public void setBackgroundColor(int backgroundColor) {
        mStyles.backgroundColor = backgroundColor;
    }

    /**
     * custom paint that can be used.
     * this will ignore the thickness and color styles.
     *
     * @param customPaint the custom paint to be used for rendering the line
     */
    public void setCustomPaint(Paint customPaint) {
        this.mCustomPaint = customPaint;
    }
}
