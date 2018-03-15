package info.nightscout.androidaps.plugins.Treatments.fragments;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.data.Iob;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.ProfileSwitch;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.interfaces.InsulinInterface;

/**
 * Created by Adrian on 15.04.2018.
 */

public class ProfileGraph extends GraphView {

    public ProfileGraph(Context context) {
        super(context);
    }

    public ProfileGraph(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void show(Profile profile) {
        removeAllSeries();

        LineGraphSeries<DataPoint> basalSeries = null;
        List<DataPoint> basalArray = new ArrayList<>();

        for (int hour = 0; hour < 24; hour++) {
            basalArray.add(new DataPoint(hour, profile.getBasal(new Integer(hour*60*60))));
            basalArray.add(new DataPoint(hour+1, profile.getBasal(new Integer(hour*60*60))));
        }
        DataPoint[] basalDataPoints = new DataPoint[basalArray.size()];
        basalDataPoints = basalArray.toArray(basalDataPoints);
        addSeries(basalSeries = new LineGraphSeries<>(basalDataPoints));
        basalSeries.setThickness(8);
        basalSeries.setDrawBackground(true);

        getViewport().setXAxisBoundsManual(true);
        getViewport().setMinX(0);
        getViewport().setMaxX(24);

        getViewport().setYAxisBoundsManual(true);
        getViewport().setMinY(0);
        getViewport().setMaxY(profile.getMaxDailyBasal()*1.1);

        getGridLabelRenderer().setNumHorizontalLabels(13);
        getGridLabelRenderer().setVerticalLabelsColor(basalSeries.getColor());
    }
}
