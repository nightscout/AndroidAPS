package info.nightscout.androidaps.plugins.Overview;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.series.PointsGraphSeries;
import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.events.EventNewBG;
import info.nightscout.androidaps.events.EventTempBasalChange;
import info.nightscout.androidaps.plugins.PluginBase;
import info.nightscout.client.data.NSProfile;


public class OverviewFragment extends Fragment implements PluginBase {
    private static Logger log = LoggerFactory.getLogger(OverviewFragment.class);

    TextView bgView;
    TextView timeAgoView;
    TextView deltaView;
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
        bgView = (TextView) view.findViewById(R.id.overview_bg);
        timeAgoView = (TextView) view.findViewById(R.id.overview_timeago);
        deltaView = (TextView) view.findViewById(R.id.overview_delta);
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
        BgReading actualBG = MainApp.getDbHelper().actualBg();
        BgReading lastBG = MainApp.getDbHelper().lastBg();
        NSProfile profile = MainApp.getNSProfile();
        if (profile == null)
            return;

        String units = profile.getUnits();

        // Skip if not initialized yet
        if (bgGraph == null)
            return;

        // **** BG value ****
        if (profile != null && lastBG != null && bgView != null) {
            bgView.setText(lastBG.valueToUnitsToString(profile.getUnits()));
            DatabaseHelper.GlucoseStatus glucoseStatus = MainApp.getDbHelper().getGlucoseStatusData();
            deltaView.setText("Δ " + NSProfile.toUnitsString(glucoseStatus.delta, glucoseStatus.delta * Constants.MGDL_TO_MMOLL, units) + " " + units);
            BgReading.units = profile.getUnits();
        } else
            return;

        Integer flag = bgView.getPaintFlags();
        if (actualBG == null) {
            flag |= Paint.STRIKE_THRU_TEXT_FLAG;
        } else
            flag &= ~Paint.STRIKE_THRU_TEXT_FLAG;
        bgView.setPaintFlags(flag);

        Long agoMsec = new Date().getTime() - lastBG.timestamp;
        int agoMin = (int) (agoMsec / 60d / 1000d);
        timeAgoView.setText(agoMin + " " + getString(R.string.minago));

        // **** BG graph ****
        // allign to hours
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(new Date().getTime());
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.add(Calendar.HOUR, 1);

        int hoursToFetch = 6;
        long toTime = calendar.getTimeInMillis();
        long fromTime = toTime - hoursToFetch * 60 * 60 * 1000l;

        Double lowLine = NSProfile.toUnits(80d, 4d, units); // TODO: make this customisable
        Double highLine = NSProfile.toUnits(180d, 10d, units);
        Double maxY = NSProfile.toUnits(400d , 20d, units); // TODO: add some scale support

        List<BgReading> bgReadingsArray = MainApp.getDbHelper().getDataFromTime(fromTime);
        List<BgReading> inRangeArray = new ArrayList<BgReading>();
        List<BgReading> outOfRangeArray = new ArrayList<BgReading>();

        if (bgReadingsArray.size() == 0)
            return;

        Iterator<BgReading> it = bgReadingsArray.iterator();
        while (it.hasNext()) {
            BgReading bg = it.next();
            if (bg.valueToUnits(units) < lowLine || bg.valueToUnits(units) > highLine)
                outOfRangeArray.add(bg);
            else
                inRangeArray.add(bg);
        }
        BgReading[] inRange = new BgReading[inRangeArray.size()];
        BgReading[] outOfRange = new BgReading[outOfRangeArray.size()];
        inRange = inRangeArray.toArray(inRange);
        outOfRange = outOfRangeArray.toArray(outOfRange);


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


        if (inRange.length > 0) {
            PointsGraphSeries<BgReading> seriesInRage = new PointsGraphSeries<BgReading>(inRange);
            bgGraph.addSeries(seriesInRage);
            seriesInRage.setShape(PointsGraphSeries.Shape.POINT);
            seriesInRage.setSize(5);
            seriesInRage.setColor(Color.GREEN);
        }

        if (outOfRange.length > 0) {
            PointsGraphSeries<BgReading> seriesOutOfRange = new PointsGraphSeries<BgReading>(outOfRange);
            bgGraph.addSeries(seriesOutOfRange);
            seriesOutOfRange.setShape(PointsGraphSeries.Shape.POINT);
            seriesOutOfRange.setSize(5);
            seriesOutOfRange.setColor(Color.RED);
        }

        // set manual x bounds to have nice steps
        bgGraph.getViewport().setMaxX(toTime);
        bgGraph.getViewport().setMinX(fromTime);
        bgGraph.getViewport().setXAxisBoundsManual(true);
        bgGraph.getGridLabelRenderer().setLabelFormatter(new TimeAsXAxisLabelFormatter(getActivity(), "HH"));
        bgGraph.getGridLabelRenderer().setNumHorizontalLabels(7); // only 7 because of the space

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
