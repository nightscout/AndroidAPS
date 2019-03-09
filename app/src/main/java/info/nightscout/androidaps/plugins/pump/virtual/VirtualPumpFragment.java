package info.nightscout.androidaps.plugins.pump.virtual;


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
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.plugins.common.SubscriberFragment;
import info.nightscout.androidaps.plugins.general.actions.defs.CustomAction;
import info.nightscout.androidaps.plugins.general.actions.defs.CustomActionType;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.virtual.events.EventVirtualPumpUpdateGui;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.FabricPrivacy;


public class VirtualPumpFragment extends SubscriberFragment {
    private static Logger log = LoggerFactory.getLogger(VirtualPumpFragment.class);

    TextView basaBasalRateView;
    TextView tempBasalView;
    TextView extendedBolusView;
    TextView batteryView;
    TextView reservoirView;
    TextView pumpTypeView;
    TextView pumpSettingsView;


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
            View view = inflater.inflate(R.layout.virtualpump_fragment, container, false);
            basaBasalRateView = (TextView) view.findViewById(R.id.virtualpump_basabasalrate);
            tempBasalView = (TextView) view.findViewById(R.id.virtualpump_tempbasal);
            extendedBolusView = (TextView) view.findViewById(R.id.virtualpump_extendedbolus);
            batteryView = (TextView) view.findViewById(R.id.virtualpump_battery);
            reservoirView = (TextView) view.findViewById(R.id.virtualpump_reservoir);
            pumpTypeView = (TextView) view.findViewById(R.id.virtualpump_type);
            pumpSettingsView = (TextView) view.findViewById(R.id.virtualpump_type_def);

            return view;
        } catch (Exception e) {
            FabricPrivacy.logException(e);
        }

        return null;
    }

    @Subscribe
    public void onStatusEvent(final EventVirtualPumpUpdateGui ev) {
        updateGUI();
    }

    @Override
    protected void updateGUI() {
        Activity activity = getActivity();
        if (activity != null && basaBasalRateView != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    VirtualPumpPlugin virtualPump = VirtualPumpPlugin.getPlugin();
                    basaBasalRateView.setText(virtualPump.getBaseBasalRate() + "U");
                    TemporaryBasal activeTemp = TreatmentsPlugin.getPlugin().getTempBasalFromHistory(System.currentTimeMillis());
                    if (activeTemp != null) {
                        tempBasalView.setText(activeTemp.toStringFull());
                    } else {
                        tempBasalView.setText("");
                    }
                    ExtendedBolus activeExtendedBolus = TreatmentsPlugin.getPlugin().getExtendedBolusFromHistory(System.currentTimeMillis());
                    if (activeExtendedBolus != null) {
                        extendedBolusView.setText(activeExtendedBolus.toString());
                    } else {
                        extendedBolusView.setText("");
                    }
                    batteryView.setText(virtualPump.batteryPercent + "%");
                    reservoirView.setText(virtualPump.reservoirInUnits + "U");

                    virtualPump.refreshConfiguration();

                    PumpType pumpType = virtualPump.getPumpType();

                    pumpTypeView.setText(pumpType.getDescription());

                    String template = MainApp.gs(R.string.virtualpump_pump_def);


                    pumpSettingsView.setText(pumpType.getFullDescription(template, pumpType.hasExtendedBasals()));

                }
            });
    }


}
