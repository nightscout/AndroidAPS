package info.nightscout.androidaps.plugins.PumpVirtual;


import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.PumpVirtual.events.EventVirtualPumpUpdateGui;

public class VirtualPumpFragment extends Fragment {
    private static Logger log = LoggerFactory.getLogger(VirtualPumpFragment.class);

    private static VirtualPumpPlugin virtualPumpPlugin = new VirtualPumpPlugin();

    public static VirtualPumpPlugin getPlugin() {
        return virtualPumpPlugin;
    }

    TextView basaBasalRateView;
    TextView tempBasalView;
    TextView extendedBolusView;
    TextView batteryView;
    TextView reservoirView;

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
        View view = inflater.inflate(R.layout.vitualpump_fragment, container, false);
        basaBasalRateView = (TextView) view.findViewById(R.id.virtualpump_basabasalrate);
        tempBasalView = (TextView) view.findViewById(R.id.virtualpump_tempbasal);
        extendedBolusView = (TextView) view.findViewById(R.id.virtualpump_extendedbolus);
        batteryView = (TextView) view.findViewById(R.id.virtualpump_battery);
        reservoirView = (TextView) view.findViewById(R.id.virtualpump_reservoir);

        updateGUI();
        return view;
    }

    @Override
    public void onPause() {
        super.onPause();
        MainApp.bus().unregister(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        MainApp.bus().register(this);
    }

    @Subscribe
    public void onStatusEvent(final EventVirtualPumpUpdateGui ev) {
        updateGUI();
    }

    public void updateGUI() {
        Activity activity = getActivity();
        if (activity != null && basaBasalRateView != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    basaBasalRateView.setText(virtualPumpPlugin.getBaseBasalRate() + "U");
                    if (virtualPumpPlugin.isTempBasalInProgress()) {
                        tempBasalView.setText(virtualPumpPlugin.getTempBasal().toString());
                    } else {
                        tempBasalView.setText("");
                    }
                    if (virtualPumpPlugin.isExtendedBoluslInProgress()) {
                        extendedBolusView.setText(virtualPumpPlugin.getExtendedBolus().toString());
                    } else {
                        extendedBolusView.setText("");
                    }
                    batteryView.setText(VirtualPumpPlugin.batteryPercent + "%");
                    reservoirView.setText(VirtualPumpPlugin.reservoirInUnits + "U");
                }
            });
    }
}
