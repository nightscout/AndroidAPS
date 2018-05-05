package info.nightscout.androidaps.plugins.Overview.graphExtensions;

/**
 * GraphView
 * Copyright (C) 2014  Jonas Gehring
 * <p>
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License,
 * with the "Linking Exception", which can be found at the license.txt
 * file in this program.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * with the "Linking Exception" along with this program; if not,
 * write to the author Jonas Gehring <g.jjoe64@gmail.com>.
 * <p>
 * Added by mike
 */

/**
 * Added by mike
 */

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.BaseSeries;

import java.util.Iterator;

import info.nightscout.androidaps.MainApp;

// Added by Rumen for scalable text

/**
 * Series that plots the data as points.
 * The points can be different shapes or a
 * complete custom drawing.
 *
 * @author jjoe64
 */
public class PointsWithLabelGraphSeries<E extends DataPointWithLabelInterface> extends BaseSeries<E> {
    // Default spSize
    int spSize = 14;
    // Convert the sp to pixels
    Context context = MainApp.instance().getApplicationContext();
    float scaledTextSize = spSize * context.getResources().getDisplayMetrics().scaledDensity;
    float scaledPxSize = context.getResources().getDisplayMetrics().scaledDensity * 3f;

    /**
     * choose a predefined shape to render for
     * each data point.
     * You can also render a custom drawing via {@link com.jjoe64.graphview.series.PointsGraphSeries.CustomShape}
     */
    public enum Shape {
        BG,
        PREDICTION,
        TRIANGLE,
        RECTANGLE,
        BOLUS,
        SMB,
        EXTENDEDBOLUS,
        PROFILE,
        MBG,
        BGCHECK,
        ANNOUNCEMENT,
        OPENAPSOFFLINE,
        EXERCISE,
        GENERAL,
        GENERALWITHDURATION,
        COBFAILOVER
    }

    /**
     * internal paint object
     */
    private Paint mPaint;

    /**
     * creates the series without data
     */
    public PointsWithLabelGraphSeries() {
        init();
    }

    /**
     * creates the series with data
     *
     * @param data datapoints
     */
    public PointsWithLabelGraphSeries(E[] data) {
        super(data);
        init();
    }

    /**
     * inits the internal objects
     * set the defaults
     */
    protected void init() {
        mPaint = new Paint();
        mPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    /**
     * plot the data to the viewport
     *
     * @param graphView graphview
     * @param canvas canvas to draw on
     * @param isSecondScale whether it is the second scale
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
        // draw data

        double diffY = maxY - minY;
        double diffX = maxX - minX;

        float graphHeight = graphView.getGraphContentHeight();
        float graphWidth = graphView.getGraphContentWidth();
        float graphLeft = graphView.getGraphContentLeft();
        float graphTop = graphView.getGraphContentTop();

        float scaleX = (float) (graphWidth / diffX);

        int i = 0;
        while (values.hasNext()) {
            E value = values.next();

            mPaint.setColor(value.getColor());

            double valY = value.getY() - minY;
            double ratY = valY / diffY;
            double y = graphHeight * ratY;

            double valX = value.getX() - minX;
            double ratX = valX / diffX;
            double x = graphWidth * ratX;

            // overdraw
            boolean overdraw = false;
            if (x > graphWidth) { // end right
                overdraw = true;
            }
            if (y < 0) { // end bottom
                overdraw = true;
            }
            if (y > graphHeight) { // end top
                overdraw = true;
            }

            long duration = value.getDuration();
            float endWithDuration = (float) (x + duration * scaleX + graphLeft + 1);
            // cut off to graph start if needed
            if (x < 0 && endWithDuration > 0) {
                x = 0;
            }

            /* Fix a bug that continue to show the DOT after Y axis */
            if (x < 0) {
                overdraw = true;
            }

            float endX = (float) x + (graphLeft + 1);
            float endY = (float) (graphTop - y) + graphHeight;
            registerDataPoint(endX, endY, value);

            float xpluslength = 0;
            if (duration > 0) {
                xpluslength = Math.min(endWithDuration, graphLeft + graphWidth);
            }

            // draw data point
            if (!overdraw) {
                if (value.getShape() == Shape.BG || value.getShape() == Shape.COBFAILOVER) {
                    mPaint.setStyle(Paint.Style.FILL);
                    mPaint.setStrokeWidth(0);
                    canvas.drawCircle(endX, endY, value.getSize() * scaledPxSize, mPaint);
                } else if (value.getShape() == Shape.PREDICTION) {
                    mPaint.setColor(value.getColor());
                    mPaint.setStyle(Paint.Style.FILL);
                    mPaint.setStrokeWidth(0);
                    canvas.drawCircle(endX, endY, scaledPxSize, mPaint);
                    mPaint.setStyle(Paint.Style.FILL);
                    mPaint.setStrokeWidth(0);
                    canvas.drawCircle(endX, endY, scaledPxSize / 3, mPaint);
                } else if (value.getShape() == Shape.RECTANGLE) {
                    canvas.drawRect(endX - scaledPxSize, endY - scaledPxSize, endX + scaledPxSize, endY + scaledPxSize, mPaint);
                } else if (value.getShape() == Shape.TRIANGLE) {
                    mPaint.setStrokeWidth(0);
                    Point[] points = new Point[3];
                    points[0] = new Point((int) endX, (int) (endY - scaledPxSize));
                    points[1] = new Point((int) (endX + scaledPxSize), (int) (endY + scaledPxSize * 0.67));
                    points[2] = new Point((int) (endX - scaledPxSize), (int) (endY + scaledPxSize * 0.67));
                    drawArrows(points, canvas, mPaint);
                } else if (value.getShape() == Shape.BOLUS) {
                    mPaint.setStrokeWidth(0);
                    Point[] points = new Point[3];
                    points[0] = new Point((int) endX, (int) (endY - scaledPxSize));
                    points[1] = new Point((int) (endX + scaledPxSize), (int) (endY + scaledPxSize * 0.67));
                    points[2] = new Point((int) (endX - scaledPxSize), (int) (endY + scaledPxSize * 0.67));
                    mPaint.setStyle(Paint.Style.FILL_AND_STROKE);
                    drawArrows(points, canvas, mPaint);
                    if (value.getLabel() != null) {
                        drawLabel45(endX, endY, value, canvas);
                    }
                } else if (value.getShape() == Shape.SMB) {
                    mPaint.setStrokeWidth(2);
                    Point[] points = new Point[3];
                    float size = value.getSize() * scaledPxSize;
                    points[0] = new Point((int) endX, (int) (endY - size));
                    points[1] = new Point((int) (endX + size), (int) (endY + size * 0.67));
                    points[2] = new Point((int) (endX - size), (int) (endY + size * 0.67));
                    mPaint.setStyle(Paint.Style.FILL_AND_STROKE);
                    drawArrows(points, canvas, mPaint);
                } else if (value.getShape() == Shape.EXTENDEDBOLUS) {
                    mPaint.setStrokeWidth(0);
                    if (value.getLabel() != null) {
                        Rect bounds = new Rect((int) endX, (int) endY + 3, (int) (xpluslength), (int) endY + 8);
                        mPaint.setStyle(Paint.Style.FILL_AND_STROKE);
                        canvas.drawRect(bounds, mPaint);
                        mPaint.setTextSize((float) (scaledTextSize));
                        mPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
                        mPaint.setFakeBoldText(true);
                        canvas.drawText(value.getLabel(), endX, endY, mPaint);
                    }
                } else if (value.getShape() == Shape.PROFILE) {
                    mPaint.setStrokeWidth(0);
                    if (value.getLabel() != null) {
                        //mPaint.setTextSize((int) (scaledPxSize * 3));
                        mPaint.setTextSize((float) (scaledTextSize * 1.2));
                        mPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                        Rect bounds = new Rect();
                        mPaint.getTextBounds(value.getLabel(), 0, value.getLabel().length(), bounds);
                        mPaint.setStyle(Paint.Style.STROKE);
                        float px = endX + bounds.height() / 2;
                        float py = (float) (graphHeight * ratY + bounds.width() + 10);
                        canvas.save();
                        canvas.rotate(-90, px, py);
                        canvas.drawText(value.getLabel(), px, py, mPaint);
                        canvas.drawRect(px - 3, bounds.top + py - 3, bounds.right + px + 3, bounds.bottom + py + 3, mPaint);
                        canvas.restore();
                    }
                } else if (value.getShape() == Shape.MBG) {
                    mPaint.setStyle(Paint.Style.STROKE);
                    mPaint.setStrokeWidth(5);
                    float w = mPaint.getStrokeWidth();
                    canvas.drawCircle(endX, endY, scaledPxSize, mPaint);
                } else if (value.getShape() == Shape.BGCHECK) {
                    mPaint.setStyle(Paint.Style.FILL_AND_STROKE);
                    mPaint.setStrokeWidth(0);
                    canvas.drawCircle(endX, endY, scaledPxSize, mPaint);
                    if (value.getLabel() != null) {
                        drawLabel45(endX, endY, value, canvas);
                    }
                } else if (value.getShape() == Shape.ANNOUNCEMENT) {
                    mPaint.setStyle(Paint.Style.FILL_AND_STROKE);
                    mPaint.setStrokeWidth(0);
                    canvas.drawCircle(endX, endY, scaledPxSize, mPaint);
                    if (value.getLabel() != null) {
                        drawLabel45(endX, endY, value, canvas);
                    }
                } else if (value.getShape() == Shape.GENERAL) {
                    mPaint.setStyle(Paint.Style.FILL_AND_STROKE);
                    mPaint.setStrokeWidth(0);
                    canvas.drawCircle(endX, endY, scaledPxSize, mPaint);
                    if (value.getLabel() != null) {
                        drawLabel45(endX, endY, value, canvas);
                    }
                } else if (value.getShape() == Shape.EXERCISE) {
                    mPaint.setStrokeWidth(0);
                    if (value.getLabel() != null) {
                        mPaint.setStrokeWidth(0);
                        mPaint.setTextSize((float) (scaledTextSize * 1.2));
                        mPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                        Rect bounds = new Rect();
                        mPaint.getTextBounds(value.getLabel(), 0, value.getLabel().length(), bounds);
                        mPaint.setStyle(Paint.Style.STROKE);
                        float px = endX;
                        float py = graphTop + 20;
                        canvas.drawText(value.getLabel(), px, py, mPaint);
                        mPaint.setStrokeWidth(5);
                        canvas.drawRect(px - 3, bounds.top + py - 3, xpluslength + 3, bounds.bottom + py + 3, mPaint);
                    }
                } else if (value.getShape() == Shape.OPENAPSOFFLINE) {
                    mPaint.setStrokeWidth(0);
                    if (value.getLabel() != null) {
                        mPaint.setStrokeWidth(0);
                        mPaint.setTextSize(scaledTextSize);
                        mPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                        Rect bounds = new Rect();
                        mPaint.getTextBounds(value.getLabel(), 0, value.getLabel().length(), bounds);
                        mPaint.setStyle(Paint.Style.STROKE);
                        float px = endX;
                        float py = graphTop + 50;
                        canvas.drawText(value.getLabel(), px, py, mPaint);
                        mPaint.setStrokeWidth(5);
                        canvas.drawRect(px - 3, bounds.top + py - 3, xpluslength + 3, bounds.bottom + py + 3, mPaint);
                    }
                } else if (value.getShape() == Shape.GENERALWITHDURATION) {
                    mPaint.setStrokeWidth(0);
                    if (value.getLabel() != null) {
                        mPaint.setStrokeWidth(0);
                        mPaint.setTextSize((float) (scaledTextSize * 1.5));
                        mPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                        Rect bounds = new Rect();
                        mPaint.getTextBounds(value.getLabel(), 0, value.getLabel().length(), bounds);
                        mPaint.setStyle(Paint.Style.STROKE);
                        float px = endX;
                        float py = graphTop + 80;
                        canvas.drawText(value.getLabel(), px, py, mPaint);
                        mPaint.setStrokeWidth(5);
                        canvas.drawRect(px - 3, bounds.top + py - 3, xpluslength + 3, bounds.bottom + py + 3, mPaint);
                    }
                }
                // set values above point
            }

            i++;
        }

    }

    /**
     * helper to render triangle
     *
     * @param point array with 3 coordinates
     * @param canvas canvas to draw on
     * @param paint paint object
     */
    private void drawArrows(Point[] point, Canvas canvas, Paint paint) {
        float[] points = new float[8];
        points[0] = point[0].x;
        points[1] = point[0].y;
        points[2] = point[1].x;
        points[3] = point[1].y;
        points[4] = point[2].x;
        points[5] = point[2].y;
        points[6] = point[0].x;
        points[7] = point[0].y;

        canvas.save();
        canvas.drawVertices(Canvas.VertexMode.TRIANGLES, 8, points, 0, null, 0, null, 0, null, 0, 0, paint);
        Path path = new Path();
        path.moveTo(point[0].x, point[0].y);
        path.lineTo(point[1].x, point[1].y);
        path.lineTo(point[2].x, point[2].y);
        canvas.drawPath(path, paint);
        canvas.restore();
    }

    void drawLabel45(float endX, float endY, E value, Canvas canvas) {
        if (value.getLabel().startsWith("~")) {
            float px = endX;
            float py = endY + scaledPxSize;
            canvas.save();
            canvas.rotate(-45, px, py);
            mPaint.setTextSize((float) (scaledTextSize * 0.8));
            mPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
            mPaint.setFakeBoldText(true);
            mPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(value.getLabel().substring(1), px - scaledPxSize, py, mPaint);
            mPaint.setTextAlign(Paint.Align.LEFT);
            canvas.restore();
        } else {
            float px = endX;
            float py = endY - scaledPxSize;
            canvas.save();
            canvas.rotate(-45, px, py);
            mPaint.setTextSize((float) (scaledTextSize * 0.8));
            mPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
            mPaint.setFakeBoldText(true);
            canvas.drawText(value.getLabel(), px + scaledPxSize, py, mPaint);
            canvas.restore();
        }
    }
} 
