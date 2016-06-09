package info.nightscout.androidaps.plugins.Overview;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.ShareCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.helper.DateAsXAxisLabelFormatter;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.series.PointsGraphSeries;
import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.events.EventNewBG;
import info.nightscout.androidaps.events.EventTempBasalChange;
import info.nightscout.androidaps.plugins.PluginBase;
import info.nightscout.client.data.NSProfile;


public class OverviewFragment extends Fragment implements PluginBase {
    private static Logger log = LoggerFactory.getLogger(OverviewFragment.class);

    TextView bg;
    GraphView bgGraph;


    @Override
    public int getType() {
        return PluginBase.GENERAL;
    }

    @Override
    public boolean isFragmentVisible() {
        return true;
    }

    public static OverviewFragment newInstance() {
        OverviewFragment fragment = new OverviewFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerBus();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.overview_fragment, container, false);
        bg = (TextView) view.findViewById(R.id.overview_bg);
        bgGraph = (GraphView) view.findViewById(R.id.overview_bggraph);

        updateData();
        return view;
    }

    private void registerBus() {
        try {
            MainApp.bus().unregister(this);
        } catch (RuntimeException x) {
            // Ignore
        }
        MainApp.bus().register(this);
    }

    private void updateData() {
        BgReading bgReading = MainApp.getDbHelper().lastBg();
        NSProfile profile = MainApp.getNSProfile();
        if (profile != null && bgReading != null && bg != null) {
            bg.setText(bgReading.valueToUnitsToString(profile.getUnits()));
            BgReading.units = profile.getUnits();
        } else
            return;

        // Skip if not initialized yet
        if (bgGraph == null)
            return;

        // allign to hours
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(new Date().getTime());
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.add(Calendar.HOUR, 1);

        int hoursToFetch = 6;
        long toTime = calendar.getTimeInMillis();
        long fromTime = toTime - hoursToFetch * 60 * 60 * 1000l;

        Double lowLine = 80d; // TODO: make this customisable
        Double highLine = 180d;
        Double maxY = 400d; // TODO: add some scale support

        String units = profile.getUnits();
        if (units.equals(Constants.MMOL)) {
            lowLine = 4d;
            highLine = 10d;
            maxY = 20d;
        }

        List<BgReading> bgReadingsArray = MainApp.getDbHelper().getDataFromTime(fromTime);
        BgReading[] bgReadings = new BgReading[bgReadingsArray.size()];
        bgReadings = bgReadingsArray.toArray(bgReadings);

        if (bgReadings.length == 0)
            return;

        PointsGraphSeries<BgReading> series = new PointsGraphSeries<BgReading>(bgReadings);
        bgGraph.addSeries(series);
        series.setShape(PointsGraphSeries.Shape.POINT);
        series.setSize(5);
        series.setColor(Color.GREEN);

        // targets
        LineGraphSeries<DataPoint> seriesLow = new LineGraphSeries<DataPoint>(new DataPoint[]{
                new DataPoint(fromTime, lowLine),
                new DataPoint(toTime, lowLine)
        });
        seriesLow.setColor(Color.RED);
        bgGraph.addSeries(seriesLow);

        LineGraphSeries<DataPoint> seriesHigh = new LineGraphSeries<DataPoint>(new DataPoint[]{
                new DataPoint(fromTime, highLine),
                new DataPoint(toTime, highLine)
        });
        seriesHigh.setColor(Color.RED);
        bgGraph.addSeries(seriesHigh);


        // set manual x bounds to have nice steps
        bgGraph.getViewport().setMaxX(toTime);
        bgGraph.getViewport().setMinX(fromTime);
        bgGraph.getViewport().setXAxisBoundsManual(true);
        bgGraph.getGridLabelRenderer().setLabelFormatter(new TimeAsXAxisLabelFormatter(getActivity(), "HH"));
        bgGraph.getGridLabelRenderer().setNumHorizontalLabels(7); // only 7 because of the space

        String test = new SimpleDateFormat("HH").format(calendar.getTimeInMillis());

        // set manual y bounds to have nice steps
        bgGraph.getViewport().setMaxY(maxY);
        bgGraph.getViewport().setMinY(0);
        bgGraph.getViewport().setYAxisBoundsManual(true);
        bgGraph.getGridLabelRenderer().setNumVerticalLabels(11);
    }

    @Subscribe
    public void onStatusEvent(final EventTempBasalChange ev) {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateData();
                }
            });
        else
            log.debug("EventTempBasalChange: Activity is null");
    }

    @Subscribe
    public void onStatusEvent(final EventNewBG ev) {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateData();
                }
            });
        else
            log.debug("EventNewBG: Activity is null");
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);

        if (isVisibleToUser)
            updateData();
    }

}
