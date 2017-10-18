package info.nightscout.androidaps.plugins.Overview.graphData;

import android.graphics.DashPathEffect;
import android.graphics.Paint;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.LabelFormatter;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.series.Series;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.plugins.IobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.plugins.IobCobCalculator.events.BasalData;
import info.nightscout.androidaps.plugins.OpenAPSAMA.DetermineBasalResultAMA;
import info.nightscout.androidaps.plugins.Overview.graphExtensions.AreaGraphSeries;
import info.nightscout.androidaps.plugins.Overview.graphExtensions.DataPointWithLabelInterface;
import info.nightscout.androidaps.plugins.Overview.graphExtensions.DoubleDataPoint;
import info.nightscout.androidaps.plugins.Overview.graphExtensions.PointsWithLabelGraphSeries;
import info.nightscout.utils.Round;

/**
 * Created by mike on 18.10.2017.
 */

public class GraphData {

    public GraphData(GraphView bgGraph) {
        this.bgGraph = bgGraph;
        units = MainApp.getConfigBuilder().getProfileUnits();
    }

    private GraphView bgGraph;
    public double maxBgValue = 0d;
    public int numOfVertLines;
    private List<BgReading> bgReadingsArray;
    private String units;

    public void addBgReadings(long fromTime, long toTime, double lowLine, double highLine, DetermineBasalResultAMA amaResult) {
        maxBgValue = 0d;
        bgReadingsArray = MainApp.getDbHelper().getBgreadingsDataFromTime(fromTime, true);
        List<DataPointWithLabelInterface> bgListArray = new ArrayList<>();

        if (bgReadingsArray.size() == 0) {
            return;
        }

        Iterator<BgReading> it = bgReadingsArray.iterator();
        while (it.hasNext()) {
            BgReading bg = it.next();
            if (bg.value > maxBgValue) maxBgValue = bg.value;
            bgListArray.add(bg);
        }
        if (amaResult != null) {
            List<BgReading> predArray = amaResult.getPredictions();
            bgListArray.addAll(predArray);
        }

        maxBgValue = Profile.fromMgdlToUnits(maxBgValue, units);
        maxBgValue = units.equals(Constants.MGDL) ? Round.roundTo(maxBgValue, 40d) + 80 : Round.roundTo(maxBgValue, 2d) + 4;
        if (highLine > maxBgValue) maxBgValue = highLine;
        numOfVertLines = units.equals(Constants.MGDL) ? (int) (maxBgValue / 40 + 1) : (int) (maxBgValue / 2 + 1);

        DataPointWithLabelInterface[] bg = new DataPointWithLabelInterface[bgListArray.size()];
        bg = bgListArray.toArray(bg);

        if (bg.length > 0) {
            addSeriesWithoutInvalidate(new PointsWithLabelGraphSeries<>(bg));
        }

    }

    public void addInRangeArea(long fromTime, long toTime, double lowLine, double highLine) {
        AreaGraphSeries<DoubleDataPoint> inRangeAreaSeries;

        DoubleDataPoint[] inRangeAreaDataPoints = new DoubleDataPoint[]{
                new DoubleDataPoint(fromTime, lowLine, highLine),
                new DoubleDataPoint(toTime, lowLine, highLine)
        };
        inRangeAreaSeries = new AreaGraphSeries<>(inRangeAreaDataPoints);
        addSeriesWithoutInvalidate(inRangeAreaSeries);
        inRangeAreaSeries.setColor(0);
        inRangeAreaSeries.setDrawBackground(true);
        inRangeAreaSeries.setBackgroundColor(MainApp.sResources.getColor(R.color.inrangebackground));
    }

    public void addBasalsToSecondScale(long fromTime, long toTime, double scale) {
        LineGraphSeries<DataPoint> basalsLineSeries;
        LineGraphSeries<DataPoint> absoluteBasalsLineSeries;
        LineGraphSeries<DataPoint> baseBasalsSeries;
        LineGraphSeries<DataPoint> tempBasalsSeries;

        Double maxBasalValueFound = 0d;

        List<DataPoint> baseBasalArray = new ArrayList<>();
        List<DataPoint> tempBasalArray = new ArrayList<>();
        List<DataPoint> basalLineArray = new ArrayList<>();
        List<DataPoint> absoluteBasalLineArray = new ArrayList<>();
        double lastLineBasal = 0;
        double lastAbsoluteLineBasal = 0;
        double lastBaseBasal = 0;
        double lastTempBasal = 0;
        for (long time = fromTime; time < toTime; time += 60 * 1000L) {
            BasalData basalData = IobCobCalculatorPlugin.getBasalData(time);
            double baseBasalValue = basalData.basal;
            double absoluteLineValue = baseBasalValue;
            double tempBasalValue = 0;
            double basal = 0d;
            if (basalData.isTempBasalRunning) {
                absoluteLineValue = tempBasalValue = basalData.tempBasalAbsolute;
                if (tempBasalValue != lastTempBasal) {
                    tempBasalArray.add(new DataPoint(time, lastTempBasal));
                    tempBasalArray.add(new DataPoint(time, basal = tempBasalValue));
                }
                if (lastBaseBasal != 0d) {
                    baseBasalArray.add(new DataPoint(time, lastBaseBasal));
                    baseBasalArray.add(new DataPoint(time, 0d));
                    lastBaseBasal = 0d;
                }
            } else {
                if (baseBasalValue != lastBaseBasal) {
                    baseBasalArray.add(new DataPoint(time, lastBaseBasal));
                    baseBasalArray.add(new DataPoint(time, basal = baseBasalValue));
                    lastBaseBasal = baseBasalValue;
                }
                if (lastTempBasal != 0) {
                    tempBasalArray.add(new DataPoint(time, lastTempBasal));
                    tempBasalArray.add(new DataPoint(time, 0d));
                }
            }

            if (baseBasalValue != lastLineBasal) {
                basalLineArray.add(new DataPoint(time, lastLineBasal));
                basalLineArray.add(new DataPoint(time, baseBasalValue));
            }
            if (absoluteLineValue != lastAbsoluteLineBasal) {
                absoluteBasalLineArray.add(new DataPoint(time, lastAbsoluteLineBasal));
                absoluteBasalLineArray.add(new DataPoint(time, basal));
            }

            lastAbsoluteLineBasal = absoluteLineValue;
            lastLineBasal = baseBasalValue;
            lastTempBasal = tempBasalValue;
            maxBasalValueFound = Math.max(maxBasalValueFound, basal);
        }

        basalLineArray.add(new DataPoint(toTime, lastLineBasal));
        baseBasalArray.add(new DataPoint(toTime, lastBaseBasal));
        tempBasalArray.add(new DataPoint(toTime, lastTempBasal));
        absoluteBasalLineArray.add(new DataPoint(toTime, lastAbsoluteLineBasal));

        DataPoint[] baseBasal = new DataPoint[baseBasalArray.size()];
        baseBasal = baseBasalArray.toArray(baseBasal);
        baseBasalsSeries = new LineGraphSeries<>(baseBasal);
        baseBasalsSeries.setDrawBackground(true);
        baseBasalsSeries.setBackgroundColor(MainApp.sResources.getColor(R.color.basebasal));
        baseBasalsSeries.setThickness(0);

        DataPoint[] tempBasal = new DataPoint[tempBasalArray.size()];
        tempBasal = tempBasalArray.toArray(tempBasal);
        tempBasalsSeries = new LineGraphSeries<>(tempBasal);
        tempBasalsSeries.setDrawBackground(true);
        tempBasalsSeries.setBackgroundColor(MainApp.sResources.getColor(R.color.tempbasal));
        tempBasalsSeries.setThickness(0);

        DataPoint[] basalLine = new DataPoint[basalLineArray.size()];
        basalLine = basalLineArray.toArray(basalLine);
        basalsLineSeries = new LineGraphSeries<>(basalLine);
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        paint.setPathEffect(new DashPathEffect(new float[]{2, 4}, 0));
        paint.setColor(MainApp.sResources.getColor(R.color.basal));
        basalsLineSeries.setCustomPaint(paint);

        DataPoint[] absoluteBasalLine = new DataPoint[absoluteBasalLineArray.size()];
        absoluteBasalLine = absoluteBasalLineArray.toArray(absoluteBasalLine);
        absoluteBasalsLineSeries = new LineGraphSeries<>(absoluteBasalLine);
        Paint absolutePaint = new Paint();
        absolutePaint.setStyle(Paint.Style.STROKE);
        absolutePaint.setStrokeWidth(4);
        absolutePaint.setColor(MainApp.sResources.getColor(R.color.basal));
        absoluteBasalsLineSeries.setCustomPaint(absolutePaint);

        bgGraph.getSecondScale().setMinY(0);
        bgGraph.getSecondScale().setMaxY(scale * maxBasalValueFound);
        bgGraph.getSecondScale().addSeries(baseBasalsSeries);
        bgGraph.getSecondScale().addSeries(tempBasalsSeries);
        bgGraph.getSecondScale().addSeries(basalsLineSeries);
        bgGraph.getSecondScale().addSeries(absoluteBasalsLineSeries);

        bgGraph.getSecondScale().setLabelFormatter(new LabelFormatter() {
            @Override
            public String formatLabel(double value, boolean isValueX) {
                return "";
            }

            @Override
            public void setViewport(Viewport viewport) {

            }
        });
    }

    public double getNearestBg(long date) {
        double bg = 0;
        for (int r = bgReadingsArray.size() - 1; r >= 0; r--) {
            BgReading reading = bgReadingsArray.get(r);
            if (reading.date > date) continue;
            bg = Profile.fromMgdlToUnits(reading.value, units);
            break;
        }
        return bg;
    }


    private void addSeriesWithoutInvalidate(Series s) {
        s.onGraphViewAttached(bgGraph);
        bgGraph.getSeries().add(s);
    }

}
