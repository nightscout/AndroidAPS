package info.nightscout.core.graph.data;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.BaseSeries;

import java.util.Iterator;

import info.nightscout.core.main.R;

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
        CARBS,
        SMB,
        EXTENDEDBOLUS,
        PROFILE,
        MBG,
        BGCHECK,
        ANNOUNCEMENT,
        OPENAPS_OFFLINE,
        EXERCISE,
        GENERAL,
        GENERAL_WITH_DURATION,
        COB_FAIL_OVER,
        IOB_PREDICTION,
        BUCKETED_BG,
        HEARTRATE,
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
     * @param data dataPoints
     */
    public PointsWithLabelGraphSeries(E[] data) {
        super(data);
        init();
    }

    /**
     * init the internal objects
     * set the defaults
     */
    protected void init() {
        mPaint = new Paint();
        mPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    /**
     * plot the data to the viewport
     *
     * @param graphView     graphview
     * @param canvas        canvas to draw on
     * @param isSecondScale whether it is the second scale
     */
    @SuppressWarnings({"deprecation"})
    @Override
    public void draw(GraphView graphView, Canvas canvas, boolean isSecondScale) {
        // Convert the sp to pixels
        float scaledTextSize = spSize * graphView.getContext().getResources().getDisplayMetrics().scaledDensity;
        float scaledPxSize = graphView.getContext().getResources().getDisplayMetrics().scaledDensity * 3f;
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

        while (values.hasNext()) {
            E value = values.next();

            mPaint.setColor(value.color(graphView.getContext()));

            double valY = value.getY() - minY;
            double ratY = valY / diffY;
            double y = graphHeight * ratY;

            double valX = value.getX() - minX;
            double ratX = valX / diffX;
            double x = graphWidth * ratX;

            // overdraw
            boolean overdraw = x > graphWidth;
            // end right
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

            float xPlusLength = 0;
            if (duration > 0) {
                xPlusLength = Math.min(endWithDuration, graphLeft + graphWidth);
            }

            // draw data point
            if (!overdraw) {
                if (value.getShape() == Shape.BG || value.getShape() == Shape.COB_FAIL_OVER) {
                    mPaint.setStyle(value.getPaintStyle());
                    mPaint.setStrokeWidth(0);
                    canvas.drawCircle(endX, endY, value.getSize() * scaledPxSize, mPaint);
                } else if (value.getShape() == Shape.BG || value.getShape() == Shape.IOB_PREDICTION || value.getShape() == Shape.BUCKETED_BG) {
                    mPaint.setColor(value.color(graphView.getContext()));
                    mPaint.setStyle(value.getPaintStyle());
                    mPaint.setStrokeWidth(0);
                    canvas.drawCircle(endX, endY, value.getSize() * scaledPxSize, mPaint);
                } else if (value.getShape() == Shape.PREDICTION) {
                    mPaint.setColor(value.color(graphView.getContext()));
                    mPaint.setStyle(value.getPaintStyle());
                    mPaint.setStrokeWidth(0);
                    canvas.drawCircle(endX, endY, scaledPxSize, mPaint);
                    mPaint.setStyle(value.getPaintStyle());
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
                    if (!value.getLabel().isEmpty())
                        drawLabel45Right(endX, endY, value, canvas, scaledPxSize, scaledTextSize);
                } else if (value.getShape() == Shape.CARBS) {
                    mPaint.setStrokeWidth(0);
                    Point[] points = new Point[3];
                    points[0] = new Point((int) endX, (int) (endY - scaledPxSize));
                    points[1] = new Point((int) (endX + scaledPxSize), (int) (endY + scaledPxSize * 0.67));
                    points[2] = new Point((int) (endX - scaledPxSize), (int) (endY + scaledPxSize * 0.67));
                    mPaint.setStyle(Paint.Style.FILL_AND_STROKE);
                    drawArrows(points, canvas, mPaint);
                    if (!value.getLabel().isEmpty())
                        drawLabel45Left(endX, endY, value, canvas, scaledPxSize, scaledTextSize);
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
                    if (!value.getLabel().isEmpty()) {
                        Rect bounds = new Rect((int) endX, (int) endY + 3, (int) (xPlusLength), (int) endY + 8);
                        mPaint.setStyle(Paint.Style.FILL_AND_STROKE);
                        canvas.drawRect(bounds, mPaint);
                        mPaint.setTextSize(scaledTextSize);
                        mPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
                        mPaint.setFakeBoldText(true);
                        canvas.drawText(value.getLabel(), endX, endY, mPaint);
                    }
                } else if (value.getShape() == Shape.HEARTRATE) {
                    mPaint.setStrokeWidth(0);
                    Rect bounds = new Rect((int) endX, (int) endY - 8, (int) (xPlusLength), (int) endY + 8);
                    mPaint.setStyle(Paint.Style.FILL_AND_STROKE);
                    canvas.drawRect(bounds, mPaint);
                } else if (value.getShape() == Shape.PROFILE) {
                    Drawable drawable = ContextCompat.getDrawable(graphView.getContext(), R.drawable.ic_ribbon_profile);
                    assert drawable != null;
                    drawable.setColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY);
                    drawable.setBounds(
                            (int) (endX - drawable.getIntrinsicWidth() / 2),
                            (int) (endY - drawable.getIntrinsicHeight() / 2),
                            (int) (endX + drawable.getIntrinsicWidth() / 2),
                            (int) (endY + drawable.getIntrinsicHeight() / 2));
                    drawable.draw(canvas);

                    mPaint.setTextSize(scaledTextSize * 0.8f);
                    mPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
                    mPaint.setColor(value.color(graphView.getContext()));
                    Rect bounds = new Rect();
                    mPaint.getTextBounds(value.getLabel(), 0, value.getLabel().length(), bounds);
                    float px = endX - bounds.width() / 2.0f;
                    float py = endY + drawable.getIntrinsicHeight();
                    mPaint.setStyle(Paint.Style.FILL);
                    canvas.drawText(value.getLabel(), px, py, mPaint);
                } else if (value.getShape() == Shape.MBG) {
                    mPaint.setStyle(Paint.Style.STROKE);
                    mPaint.setStrokeWidth(5);
                    canvas.drawCircle(endX, endY, scaledPxSize, mPaint);
                } else if (value.getShape() == Shape.BGCHECK) {
                    mPaint.setStyle(Paint.Style.FILL_AND_STROKE);
                    mPaint.setStrokeWidth(0);
                    canvas.drawCircle(endX, endY, scaledPxSize, mPaint);
                    if (!value.getLabel().isEmpty()) {
                        drawLabel45Right(endX, endY, value, canvas, scaledPxSize, scaledTextSize);
                    }
                } else if (value.getShape() == Shape.ANNOUNCEMENT) {
                    mPaint.setStyle(Paint.Style.FILL_AND_STROKE);
                    mPaint.setStrokeWidth(0);
                    canvas.drawCircle(endX, endY, scaledPxSize, mPaint);
                    if (!value.getLabel().isEmpty()) {
                        drawLabel45Right(endX, endY, value, canvas, scaledPxSize, scaledTextSize);
                    }
                } else if (value.getShape() == Shape.GENERAL) {
                    mPaint.setStyle(Paint.Style.FILL_AND_STROKE);
                    mPaint.setStrokeWidth(0);
                    canvas.drawCircle(endX, endY, scaledPxSize, mPaint);
                    if (!value.getLabel().isEmpty()) {
                        drawLabel45Right(endX, endY, value, canvas, scaledPxSize, scaledTextSize);
                    }
                } else if (value.getShape() == Shape.EXERCISE) {
                    mPaint.setStrokeWidth(0);
                    if (!value.getLabel().isEmpty()) {
                        mPaint.setStrokeWidth(0);
                        mPaint.setTextSize((float) (scaledTextSize * 1.2));
                        mPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                        Rect bounds = new Rect();
                        mPaint.getTextBounds(value.getLabel(), 0, value.getLabel().length(), bounds);
                        mPaint.setStyle(Paint.Style.STROKE);
                        float py = graphTop + 20;
                        canvas.drawText(value.getLabel(), endX, py, mPaint);
                        mPaint.setStrokeWidth(5);
                        canvas.drawRect(endX - 3, bounds.top + py - 3, xPlusLength + 3, bounds.bottom + py + 3, mPaint);
                    }
                } else if (value.getShape() == Shape.OPENAPS_OFFLINE && value.getDuration() != 0) {
                    mPaint.setStrokeWidth(0);
                    if (!value.getLabel().isEmpty()) {
                        mPaint.setStyle(Paint.Style.FILL_AND_STROKE);
                        mPaint.setStrokeWidth(5);
                        canvas.drawRect(endX - 3, graphTop, xPlusLength + 3, graphTop + graphHeight, mPaint);
                    }
                } else if (value.getShape() == Shape.GENERAL_WITH_DURATION) {
                    mPaint.setStrokeWidth(0);
                    if (!value.getLabel().isEmpty()) {
                        mPaint.setStrokeWidth(0);
                        mPaint.setTextSize((float) (scaledTextSize * 1.5));
                        mPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                        Rect bounds = new Rect();
                        mPaint.getTextBounds(value.getLabel(), 0, value.getLabel().length(), bounds);
                        mPaint.setStyle(Paint.Style.STROKE);
                        float py = graphTop + 80;
                        canvas.drawText(value.getLabel(), endX, py, mPaint);
                        mPaint.setStrokeWidth(5);
                        canvas.drawRect(endX - 3, bounds.top + py - 3, xPlusLength + 3, bounds.bottom + py + 3, mPaint);
                    }
                }
                // set values above point
            }

        }

    }

    /**
     * helper to render triangle
     *
     * @param point  array with 3 coordinates
     * @param canvas canvas to draw on
     * @param paint  paint object
     */
    private void drawArrows(Point[] point, Canvas canvas, Paint paint) {
        canvas.save();
        Path path = new Path();
        path.moveTo(point[0].x, point[0].y);
        path.lineTo(point[1].x, point[1].y);
        path.lineTo(point[2].x, point[2].y);
        path.close();
        canvas.drawPath(path, paint);
        canvas.restore();
    }

    void drawLabel45Right(float endX, float endY, E value, Canvas canvas, Float scaledPxSize, Float scaledTextSize) {
        float py = endY - scaledPxSize;
        canvas.save();
        canvas.rotate(-45, endX, py);
        mPaint.setTextSize((float) (scaledTextSize * 0.8));
        mPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        mPaint.setFakeBoldText(true);
        canvas.drawText(value.getLabel(), endX + scaledPxSize, py, mPaint);
        canvas.restore();
    }

    void drawLabel45Left(float endX, float endY, E value, Canvas canvas, Float scaledPxSize, Float scaledTextSize) {
        float py = endY + scaledPxSize;
        canvas.save();
        canvas.rotate(-45, endX, py);
        mPaint.setTextSize((float) (scaledTextSize * 0.8));
        mPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        mPaint.setFakeBoldText(true);
        mPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(value.getLabel(), endX - scaledPxSize, py, mPaint);
        mPaint.setTextAlign(Paint.Align.LEFT);
        canvas.restore();
    }
}
