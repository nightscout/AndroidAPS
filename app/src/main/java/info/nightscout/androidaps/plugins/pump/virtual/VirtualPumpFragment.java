package info.nightscout.androidaps.plugins.pump.virtual;


import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.virtual.events.EventVirtualPumpUpdateGui;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.FabricPrivacy;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;


public class VirtualPumpFragment extends Fragment {
    private static Logger log = LoggerFactory.getLogger(VirtualPumpFragment.class);
    private CompositeDisposable disposable = new CompositeDisposable();

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
            sRefreshLoop = () -> {
                Activity activity = getActivity();
                if (activity != null)
                    activity.runOnUiThread(this::updateGui);
                sLoopHandler.postDelayed(sRefreshLoop, 60 * 1000L);
            };
            sLoopHandler.postDelayed(sRefreshLoop, 60 * 1000L);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.virtualpump_fragment, container, false);
        basaBasalRateView = (TextView) view.findViewById(R.id.virtualpump_basabasalrate);
        tempBasalView = (TextView) view.findViewById(R.id.virtualpump_tempbasal);
        extendedBolusView = (TextView) view.findViewById(R.id.virtualpump_extendedbolus);
        batteryView = (TextView) view.findViewById(R.id.virtualpump_battery);
        reservoirView = (TextView) view.findViewById(R.id.virtualpump_reservoir);
        pumpTypeView = (TextView) view.findViewById(R.id.virtualpump_type);
        pumpSettingsView = (TextView) view.findViewById(R.id.virtualpump_type_def);

        return view;
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        disposable.add(RxBus.INSTANCE
                .toObservable(EventVirtualPumpUpdateGui.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> updateGui(), FabricPrivacy::logException)
        );
        updateGui();
    }

    @Override
    public synchronized void onPause() {
        super.onPause();
        disposable.clear();
    }

    protected void updateGui() {
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
}
