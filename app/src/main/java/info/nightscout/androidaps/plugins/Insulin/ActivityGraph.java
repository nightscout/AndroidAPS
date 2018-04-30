package info.nightscout.androidaps.plugins.Insulin;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.data.Iob;
import info.nightscout.androidaps.plugins.Treatments.Treatment;
import info.nightscout.androidaps.interfaces.InsulinInterface;

/**
 * Created by mike on 21.04.2017.
 */

public class ActivityGraph extends GraphView {
    Context context;

    public ActivityGraph(Context context) {
        super(context);
        this.context = context;
    }

    public ActivityGraph(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
    }

    public void show(InsulinInterface insulin) {
        removeAllSeries();
        mSecondScale = null;
        double dia = insulin.getDia();
        int hours = (int) Math.floor(dia + 1);

        Treatment t = new Treatment();
        t.date = 0;
        t.insulin = 1d;

        LineGraphSeries<DataPoint> activitySeries = null;
        LineGraphSeries<DataPoint> iobSeries = null;
        List<DataPoint> activityArray = new ArrayList<>();
        List<DataPoint> iobArray = new ArrayList<>();

        for (long time = 0; time <= hours * 60 * 60 * 1000; time += 5 * 60 * 1000L) {
            Iob iob = t.iobCalc(time, dia);
            activityArray.add(new DataPoint(time / 60 / 1000, iob.activityContrib));
            iobArray.add(new DataPoint(time / 60 / 1000, iob.iobContrib));
        }

        DataPoint[] activityDataPoints = new DataPoint[activityArray.size()];
        activityDataPoints = activityArray.toArray(activityDataPoints);
        addSeries(activitySeries = new LineGraphSeries<>(activityDataPoints));
        activitySeries.setThickness(8);

        getViewport().setXAxisBoundsManual(true);
        getViewport().setMinX(0);
        getViewport().setMaxX(hours * 60);
        getGridLabelRenderer().setNumHorizontalLabels(hours + 1);
        getGridLabelRenderer().setHorizontalAxisTitle("[min]");
        getGridLabelRenderer().setVerticalLabelsColor(activitySeries.getColor());

        DataPoint[] iobDataPoints = new DataPoint[iobArray.size()];
        iobDataPoints = iobArray.toArray(iobDataPoints);
        getSecondScale().addSeries(iobSeries = new LineGraphSeries<>(iobDataPoints));
        iobSeries.setDrawBackground(true);
        iobSeries.setColor(Color.MAGENTA);
        iobSeries.setBackgroundColor(Color.argb(70, 255, 0, 255));
        getSecondScale().setMinY(0);
        getSecondScale().setMaxY(1);
        getGridLabelRenderer().setVerticalLabelsSecondScaleColor(Color.MAGENTA);
    }
}
