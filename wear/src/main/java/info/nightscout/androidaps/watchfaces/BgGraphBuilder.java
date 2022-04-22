package info.nightscout.androidaps.watchfaces;

import android.content.Context;
import android.graphics.DashPathEffect;
import android.preference.PreferenceManager;
import android.text.format.DateFormat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import info.nightscout.shared.weardata.EventData;
import lecho.lib.hellocharts.model.Axis;
import lecho.lib.hellocharts.model.AxisValue;
import lecho.lib.hellocharts.model.Line;
import lecho.lib.hellocharts.model.LineChartData;
import lecho.lib.hellocharts.model.PointValue;

/**
 * Created by emmablack on 11/15/14.
 */
public class BgGraphBuilder {
    public static final double MAX_PREDICTION__TIME_RATIO = (3d / 5);
    public static final double UPPER_CUTOFF_SGV = 400;
    private final long predictionEndTime;
    private final List<EventData.SingleBg> predictionsList;
    private final ArrayList<EventData.TreatmentData.Treatment> bolusWatchDataList;
    private final ArrayList<EventData.TreatmentData.Basal> basalWatchDataList;
    public List<EventData.TreatmentData.TempBasal> tempWatchDataList;
    private final int timespan;
    public long end_time;
    public long start_time;
    public double fuzzyTimeDenom = (1000 * 60 * 1);
    public Context context;
    public double highMark;
    public double lowMark;
    public List<EventData.SingleBg> bgDataList;

    public int pointSize;
    public int highColor;
    public int lowColor;
    public int midColor;
    public int gridColour;
    public int basalCenterColor;
    public int basalBackgroundColor;
    private final int bolusInvalidColor;
    private final int carbsColor;

    public boolean singleLine = false;

    private final List<PointValue> inRangeValues = new ArrayList<>();
    private final List<PointValue> highValues = new ArrayList<>();
    private final List<PointValue> lowValues = new ArrayList<>();

    //used for low resolution screen.
    public BgGraphBuilder(Context context, List<EventData.SingleBg> aBgList,
                          List<EventData.SingleBg> predictionsList,
                          List<EventData.TreatmentData.TempBasal> tempWatchDataList,
                          ArrayList<EventData.TreatmentData.Basal> basalWatchDataList,
                          ArrayList<EventData.TreatmentData.Treatment> bolusWatchDataList,
                          int aPointSize, int aMidColor, int gridColour, int basalBackgroundColor, int basalCenterColor, int bolusInvalidColor, int carbsColor, int timespan) {
        this.start_time = System.currentTimeMillis() - (1000L * 60 * 60 * timespan); //timespan
        // hours ago
        this.bgDataList = aBgList;
        this.predictionsList = predictionsList;
        this.context = context;
        this.highMark = aBgList.get(aBgList.size() - 1).getHigh();
        this.lowMark = aBgList.get(aBgList.size() - 1).getLow();
        this.pointSize = aPointSize;
        this.singleLine = false;
        this.midColor = aMidColor;
        this.lowColor = aMidColor;
        this.highColor = aMidColor;
        this.timespan = timespan;
        this.tempWatchDataList = tempWatchDataList;
        this.basalWatchDataList = basalWatchDataList;
        this.bolusWatchDataList = (bolusWatchDataList != null) ? bolusWatchDataList : new ArrayList<>();
        this.gridColour = gridColour;
        this.basalCenterColor = basalCenterColor;
        this.basalBackgroundColor = basalBackgroundColor;
        this.bolusInvalidColor = bolusInvalidColor;
        this.carbsColor = carbsColor;
        this.end_time = System.currentTimeMillis() + (1000L * 60 * 6 * timespan); //Now plus 30
        // minutes padding (for 5 hours. Less if less.)
        this.predictionEndTime = getPredictionEndTime();
        this.end_time = Math.max(predictionEndTime, end_time);
    }

    public BgGraphBuilder(Context context, List<EventData.SingleBg> aBgList,
                          List<EventData.SingleBg> predictionsList,
                          List<EventData.TreatmentData.TempBasal> tempWatchDataList,
                          ArrayList<EventData.TreatmentData.Basal> basalWatchDataList,
                          ArrayList<EventData.TreatmentData.Treatment> bolusWatchDataList,
                          int aPointSize, int aHighColor, int aLowColor, int aMidColor, int gridColour, int basalBackgroundColor, int basalCenterColor, int bolusInvalidColor, int carbsColor, int timespan) {
        this.start_time = System.currentTimeMillis() - (1000L * 60 * 60 * timespan); //timespan
        // hours ago
        this.bgDataList = aBgList;
        this.predictionsList = predictionsList;
        this.context = context;
        this.highMark = aBgList.get(aBgList.size() - 1).getHigh();
        this.lowMark = aBgList.get(aBgList.size() - 1).getLow();
        this.pointSize = aPointSize;
        this.highColor = aHighColor;
        this.lowColor = aLowColor;
        this.midColor = aMidColor;
        this.timespan = timespan;
        this.tempWatchDataList = tempWatchDataList;
        this.basalWatchDataList = basalWatchDataList;
        this.bolusWatchDataList = (bolusWatchDataList != null) ? bolusWatchDataList : new ArrayList<>();
        this.gridColour = gridColour;
        this.basalCenterColor = basalCenterColor;
        this.basalBackgroundColor = basalBackgroundColor;
        this.bolusInvalidColor = bolusInvalidColor;
        this.carbsColor = carbsColor;
        this.end_time = System.currentTimeMillis() + (1000L * 60 * 6 * timespan); //Now plus 30
        // minutes padding (for 5 hours. Less if less.)
        this.predictionEndTime = getPredictionEndTime();
        this.end_time = Math.max(predictionEndTime, end_time);
    }

    public LineChartData lineData() {
        LineChartData lineData = new LineChartData(defaultLines());
        lineData.setAxisYLeft(yAxis());
        lineData.setAxisXBottom(xAxis());
        return lineData;
    }

    public List<Line> defaultLines() {

        addBgReadingValues();
        List<Line> lines = new ArrayList<>();
        lines.add(highLine());
        lines.add(lowLine());
        lines.add(inRangeValuesLine());
        lines.add(lowValuesLine());
        lines.add(highValuesLine());

        double minChart = lowMark;
        double maxChart = highMark;

        for (EventData.SingleBg bgd : bgDataList) {
            if (bgd.getSgv() > maxChart) {
                maxChart = bgd.getSgv();
            }
            if (bgd.getSgv() < minChart) {
                minChart = bgd.getSgv();
            }
        }

        double maxBasal = 0.1;
        for (EventData.TreatmentData.Basal bwd : basalWatchDataList) {
            if (bwd.getAmount() > maxBasal) {
                maxBasal = bwd.getAmount();
            }
        }

        double maxTemp = maxBasal;
        for (EventData.TreatmentData.TempBasal twd : tempWatchDataList) {
            if (twd.getAmount() > maxTemp) {
                maxTemp = twd.getAmount();
            }
        }

        double factor = (maxChart - minChart) / maxTemp;
        // in case basal is the highest, don't paint it totally at the top.
        factor = Math.min(factor, ((maxChart - minChart) / maxBasal) * (2 / 3d));

        boolean highlight = PreferenceManager
                .getDefaultSharedPreferences(context)
                .getBoolean("highlight_basals", false);

        for (EventData.TreatmentData.TempBasal twd : tempWatchDataList) {
            if (twd.getEndTime() > start_time) {
                lines.add(tempValuesLine(twd, (float) minChart, factor, false, highlight ? (pointSize + 1) : pointSize));
                if (highlight) {
                    lines.add(tempValuesLine(twd, (float) minChart, factor, true, 1));
                }
            }
        }

        addPredictionLines(lines);
        lines.add(basalLine((float) minChart, factor, highlight));
        lines.add(bolusLine((float) minChart));
        lines.add(bolusInvalidLine((float) minChart));
        lines.add(carbsLine((float) minChart));
        lines.add(smbLine((float) minChart));

        return lines;
    }

    private Line basalLine(float offset, double factor, boolean highlight) {

        List<PointValue> pointValues = new ArrayList<>();

        for (EventData.TreatmentData.Basal bwd : basalWatchDataList) {
            if (bwd.getEndTime() > start_time) {
                long begin = Math.max(start_time, bwd.getStartTime());
                pointValues.add(new PointValue(fuzz(begin), offset + (float) (factor * bwd.getAmount())));
                pointValues.add(new PointValue(fuzz(bwd.getEndTime()),
                        offset + (float) (factor * bwd.getAmount())));
            }
        }

        Line basalLine = new Line(pointValues);
        basalLine.setHasPoints(false);
        basalLine.setColor(basalCenterColor);
        basalLine.setPathEffect(new DashPathEffect(new float[]{4f, 3f}, 4f));
        basalLine.setStrokeWidth(highlight ? 2 : 1);
        return basalLine;


    }

    private Line bolusLine(float offset) {

        List<PointValue> pointValues = new ArrayList<>();

        for (EventData.TreatmentData.Treatment bwd : bolusWatchDataList) {
            if (bwd.getDate() > start_time && bwd.getDate() <= end_time && !bwd.isSMB() && bwd.isValid() && bwd.getBolus() > 0) {
                pointValues.add(new PointValue(fuzz(bwd.getDate()), offset - 2));
            }
        }
        Line line = new Line(pointValues);
        line.setColor(basalCenterColor);
        line.setHasLines(false);
        line.setPointRadius(pointSize * 2);
        line.setHasPoints(true);
        return line;
    }

    private Line smbLine(float offset) {

        List<PointValue> pointValues = new ArrayList<>();

        for (EventData.TreatmentData.Treatment bwd : bolusWatchDataList) {
            if (bwd.getDate() > start_time && bwd.getDate() <= end_time && bwd.isSMB() && bwd.isValid() && bwd.getBolus() > 0) {
                pointValues.add(new PointValue(fuzz(bwd.getDate()), offset - 2));
            }
        }
        Line line = new Line(pointValues);
        line.setColor(basalCenterColor);
        line.setHasLines(false);
        line.setPointRadius(pointSize);
        line.setHasPoints(true);
        return line;
    }

    private Line bolusInvalidLine(float offset) {

        List<PointValue> pointValues = new ArrayList<>();

        for (EventData.TreatmentData.Treatment bwd : bolusWatchDataList) {
            if (bwd.getDate() > start_time && bwd.getDate() <= end_time && !(bwd.isValid() && (bwd.getBolus() > 0 || bwd.getCarbs() > 0))) {
                pointValues.add(new PointValue(fuzz(bwd.getDate()), offset - 2));
            }
        }
        Line line = new Line(pointValues);
        line.setColor(bolusInvalidColor);
        line.setHasLines(false);
        line.setPointRadius(pointSize);
        line.setHasPoints(true);
        return line;
    }

    private Line carbsLine(float offset) {

        List<PointValue> pointValues = new ArrayList<>();

        for (EventData.TreatmentData.Treatment bwd : bolusWatchDataList) {
            if (bwd.getDate() > start_time && bwd.getDate() <= end_time && !bwd.isSMB() && bwd.isValid() && bwd.getCarbs() > 0) {
                pointValues.add(new PointValue(fuzz(bwd.getDate()), offset + 2));
            }
        }
        Line line = new Line(pointValues);
        line.setColor(carbsColor);
        line.setHasLines(false);
        line.setPointRadius(pointSize * 2);
        line.setHasPoints(true);
        return line;
    }


    private void addPredictionLines(List<Line> lines) {
        Map<Integer, List<PointValue>> values = new HashMap<>();
        long endTime = getPredictionEndTime();
        for (EventData.SingleBg bwd : predictionsList) {
            if (bwd.getTimeStamp() <= endTime) {
                double value = Math.min(bwd.getSgv(), UPPER_CUTOFF_SGV);
                if (!values.containsKey(bwd.getColor())) {
                    values.put(bwd.getColor(), new ArrayList<>());
                }
                values.get(bwd.getColor()).add(new PointValue(fuzz(bwd.getTimeStamp()), (float) value));
            }
        }
        for (Map.Entry<Integer, List<PointValue>> entry : values.entrySet()) {
            Line line = new Line(entry.getValue());
            line.setColor(entry.getKey());
            line.setHasLines(false);
            int size = pointSize / 2;
            size = (size > 0) ? size : 1;
            line.setPointRadius(size);
            line.setHasPoints(true);
            lines.add(line);
        }
    }

    public Line highValuesLine() {
        Line highValuesLine = new Line(highValues);
        highValuesLine.setColor(highColor);
        highValuesLine.setHasLines(false);
        highValuesLine.setPointRadius(pointSize);
        highValuesLine.setHasPoints(true);
        return highValuesLine;
    }

    public Line lowValuesLine() {
        Line lowValuesLine = new Line(lowValues);
        lowValuesLine.setColor(lowColor);
        lowValuesLine.setHasLines(false);
        lowValuesLine.setPointRadius(pointSize);
        lowValuesLine.setHasPoints(true);
        return lowValuesLine;
    }

    public Line inRangeValuesLine() {
        Line inRangeValuesLine = new Line(inRangeValues);
        inRangeValuesLine.setColor(midColor);
        if (singleLine) {
            inRangeValuesLine.setHasLines(true);
            inRangeValuesLine.setHasPoints(false);
            inRangeValuesLine.setStrokeWidth(pointSize);
        } else {
            inRangeValuesLine.setPointRadius(pointSize);
            inRangeValuesLine.setHasPoints(true);
            inRangeValuesLine.setHasLines(false);
        }
        return inRangeValuesLine;
    }


    public Line tempValuesLine(EventData.TreatmentData.TempBasal twd, float offset, double factor, boolean isHighlightLine, int strokeWidth) {
        List<PointValue> lineValues = new ArrayList<>();
        long begin = Math.max(start_time, twd.getStartTime());
        lineValues.add(new PointValue(fuzz(begin), offset + (float) (factor * twd.getStartBasal())));
        lineValues.add(new PointValue(fuzz(begin), offset + (float) (factor * twd.getAmount())));
        lineValues.add(new PointValue(fuzz(twd.getEndTime()), offset + (float) (factor * twd.getAmount())));
        lineValues.add(new PointValue(fuzz(twd.getEndTime()), offset + (float) (factor * twd.getEndBasal())));
        Line valueLine = new Line(lineValues);
        valueLine.setHasPoints(false);
        if (isHighlightLine) {
            valueLine.setColor(basalCenterColor);
            valueLine.setStrokeWidth(1);
        } else {
            valueLine.setColor(basalBackgroundColor);
            valueLine.setStrokeWidth(strokeWidth);
        }
        return valueLine;
    }


    private void addBgReadingValues() {
        if (singleLine) {
            for (EventData.SingleBg bgReading : bgDataList) {
                if (bgReading.getTimeStamp() > start_time) {
                    if (bgReading.getSgv() >= 450) {
                        inRangeValues.add(new PointValue(fuzz(bgReading.getTimeStamp()), (float) 450));
                    } else if (bgReading.getSgv() >= highMark) {
                        inRangeValues.add(new PointValue(fuzz(bgReading.getTimeStamp()), (float) bgReading.getSgv()));
                    } else if (bgReading.getSgv() >= lowMark) {
                        inRangeValues.add(new PointValue(fuzz(bgReading.getTimeStamp()), (float) bgReading.getSgv()));
                    } else if (bgReading.getSgv() >= 40) {
                        inRangeValues.add(new PointValue(fuzz(bgReading.getTimeStamp()), (float) bgReading.getSgv()));
                    } else if (bgReading.getSgv() >= 11) {
                        inRangeValues.add(new PointValue(fuzz(bgReading.getTimeStamp()), (float) 40));
                    }
                }
            }
        } else {
            for (EventData.SingleBg bgReading : bgDataList) {
                if (bgReading.getTimeStamp() > start_time) {
                    if (bgReading.getSgv() >= 450) {
                        highValues.add(new PointValue(fuzz(bgReading.getTimeStamp()), (float) 450));
                    } else if (bgReading.getSgv() >= highMark) {
                        highValues.add(new PointValue(fuzz(bgReading.getTimeStamp()), (float) bgReading.getSgv()));
                    } else if (bgReading.getSgv() >= lowMark) {
                        inRangeValues.add(new PointValue(fuzz(bgReading.getTimeStamp()), (float) bgReading.getSgv()));
                    } else if (bgReading.getSgv() >= 40) {
                        lowValues.add(new PointValue(fuzz(bgReading.getTimeStamp()), (float) bgReading.getSgv()));
                    } else if (bgReading.getSgv() >= 11) {
                        lowValues.add(new PointValue(fuzz(bgReading.getTimeStamp()), (float) 40));
                    }
                }
            }
        }
    }

    public Line highLine() {
        List<PointValue> highLineValues = new ArrayList<>();
        highLineValues.add(new PointValue(fuzz(start_time), (float) highMark));
        highLineValues.add(new PointValue(fuzz(end_time), (float) highMark));
        Line highLine = new Line(highLineValues);
        highLine.setHasPoints(false);
        highLine.setStrokeWidth(1);
        highLine.setColor(highColor);
        return highLine;
    }

    public Line lowLine() {
        List<PointValue> lowLineValues = new ArrayList<>();
        lowLineValues.add(new PointValue(fuzz(start_time), (float) lowMark));
        lowLineValues.add(new PointValue(fuzz(end_time), (float) lowMark));
        Line lowLine = new Line(lowLineValues);
        lowLine.setHasPoints(false);
        lowLine.setColor(lowColor);
        lowLine.setStrokeWidth(1);
        return lowLine;
    }

    /////////AXIS RELATED//////////////


    public Axis yAxis() {
        Axis yAxis = new Axis();
        yAxis.setAutoGenerated(true);
        List<AxisValue> axisValues = new ArrayList<>();
        yAxis.setValues(axisValues);
        yAxis.setHasLines(false);
        yAxis.setLineColor(gridColour);
        return yAxis;
    }

    public Axis xAxis() {
        final boolean is24 = DateFormat.is24HourFormat(context);
        SimpleDateFormat timeFormat = new SimpleDateFormat(is24 ? "HH" : "h a");
        timeFormat.setTimeZone(TimeZone.getDefault());
        long timeNow = System.currentTimeMillis();

        Axis xAxis = new Axis();
        xAxis.setAutoGenerated(false);
        List<AxisValue> xAxisValues = new ArrayList<>();

        //get the time-tick at the full hour after start_time
        GregorianCalendar startGC = new GregorianCalendar();
        startGC.setTimeInMillis(start_time);
        startGC.set(Calendar.MILLISECOND, 0);
        startGC.set(Calendar.SECOND, 0);
        startGC.set(Calendar.MINUTE, 0);
        startGC.add(Calendar.HOUR, 1);
        long start_hour = startGC.getTimeInMillis();

        //Display current time on the graph
        SimpleDateFormat longTimeFormat = new SimpleDateFormat(is24 ? "HH:mm" : "h:mm a");
        xAxisValues.add(new AxisValue(fuzz(timeNow)).setLabel((longTimeFormat.format(timeNow))));

        long hourTick = start_hour;

        // add all full hours within the timeframe
        while (hourTick < end_time) {
            if (Math.abs(hourTick - timeNow) > (8 * (end_time - start_time) / 60)) {
                xAxisValues.add(new AxisValue(fuzz(hourTick)).setLabel(timeFormat.format(hourTick)));
            } else {
                //don't print hour label if too close to now to avoid overlaps
                xAxisValues.add(new AxisValue(fuzz(hourTick)).setLabel(""));
            }

            //increment by one hour
            hourTick += 60 * 60 * 1000;
        }

        xAxis.setValues(xAxisValues);
        xAxis.setTextSize(10);
        xAxis.setHasLines(true);
        xAxis.setLineColor(gridColour);
        xAxis.setTextColor(gridColour);

        return xAxis;
    }

    public long getPredictionEndTime() {
        long maxPredictionDate = System.currentTimeMillis();
        for (EventData.SingleBg prediction :
                predictionsList) {
            if (maxPredictionDate < prediction.getTimeStamp()) {
                maxPredictionDate = prediction.getTimeStamp();
            }
        }
        return (long) Math.min(maxPredictionDate, System.currentTimeMillis() + MAX_PREDICTION__TIME_RATIO * timespan * 1000 * 60 * 60);
    }

    public float fuzz(long value) {
        return (float) Math.round(value / fuzzyTimeDenom);
    }
}
