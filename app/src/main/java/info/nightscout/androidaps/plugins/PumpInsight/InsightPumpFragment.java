package info.nightscout.androidaps.plugins.PumpInsight;


import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.Common.SubscriberFragment;
import info.nightscout.androidaps.plugins.PumpInsight.connector.Connector;
import info.nightscout.androidaps.plugins.PumpInsight.events.EventInsightPumpUpdateGui;

import com.crashlytics.android.Crashlytics;

public class InsightPumpFragment extends SubscriberFragment {
    private static Logger log = LoggerFactory.getLogger(InsightPumpFragment.class);

    TextView basaBasalRateView;
    TextView tempBasalView;
    TextView extendedBolusView;
    TextView batteryView;
    TextView reservoirView;
    TextView statusView;
    Connector connector = Connector.get();

    private static Handler sLoopHandler = new Handler();
    private static Runnable sRefreshLoop = null;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (sRefreshLoop == null) {
            sRefreshLoop = new Runnable() {
                @Override
                public void run() {
                    updateGUI();
                    sLoopHandler.postDelayed(sRefreshLoop, 60 * 1000L);
                }
            };
            sLoopHandler.postDelayed(sRefreshLoop, 60 * 1000L);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.insightpump_fragment, container, false);
            basaBasalRateView = (TextView) view.findViewById(R.id.insightpump_basabasalrate);
            tempBasalView = (TextView) view.findViewById(R.id.insightpump_tempbasal);
            extendedBolusView = (TextView) view.findViewById(R.id.insightpump_extendedbolus);
            batteryView = (TextView) view.findViewById(R.id.insightpump_battery);
            reservoirView = (TextView) view.findViewById(R.id.insightpump_reservoir);
            statusView = (TextView) view.findViewById(R.id.insightpump_status);

            return view;
        } catch (Exception e) {
            Crashlytics.logException(e);
        }

        return null;
    }

    @Subscribe
    public void onStatusEvent(final EventInsightPumpUpdateGui ev) {
        updateGUI();
    }

    @Override
    protected void updateGUI() {
        Activity activity = getActivity();
        if (activity != null && basaBasalRateView != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    InsightPumpPlugin insightPumpPlugin = InsightPumpPlugin.getPlugin();
                    basaBasalRateView.setText(insightPumpPlugin.getBaseBasalRateString() + "U");
                    if (MainApp.getConfigBuilder().isTempBasalInProgress()) {
                        tempBasalView.setText(MainApp.getConfigBuilder().getTempBasalFromHistory(System.currentTimeMillis()).toStringFull());
                    } else {
                        tempBasalView.setText("");
                    }
                    if (MainApp.getConfigBuilder().isInHistoryExtendedBoluslInProgress()) {
                        extendedBolusView.setText(MainApp.getConfigBuilder().getExtendedBolusFromHistory(System.currentTimeMillis()).toString());
                    } else {
                        extendedBolusView.setText("");
                    }
                    batteryView.setText(insightPumpPlugin.batteryPercent + "%");
                    reservoirView.setText(insightPumpPlugin.reservoirInUnits + "U");

                    statusView.setText(connector.getLastStatusMessage());
                }
            });
    }
}
