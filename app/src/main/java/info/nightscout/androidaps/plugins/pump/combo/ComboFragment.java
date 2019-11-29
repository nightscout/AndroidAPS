package info.nightscout.androidaps.plugins.pump.combo;


import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.pump.combo.events.EventComboPumpUpdateGUI;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.PumpState;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.history.Bolus;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.queue.events.EventQueueChanged;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.SP;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;

public class ComboFragment extends Fragment implements View.OnClickListener {
    private CompositeDisposable disposable = new CompositeDisposable();

    private TextView stateView;
    private TextView activityView;
    private TextView batteryView;
    private TextView reservoirView;
    private TextView lastConnectionView;
    private TextView lastBolusView;
    private TextView baseBasalRate;
    private TextView tempBasalText;
    private Button refreshButton;
    private TextView bolusCount;
    private TextView tbrCount;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.combopump_fragment, container, false);

        stateView = view.findViewById(R.id.combo_state);
        activityView = view.findViewById(R.id.combo_activity);
        batteryView = view.findViewById(R.id.combo_pumpstate_battery);
        reservoirView = view.findViewById(R.id.combo_insulinstate);
        lastBolusView = view.findViewById(R.id.combo_last_bolus);
        lastConnectionView = view.findViewById(R.id.combo_lastconnection);
        baseBasalRate = view.findViewById(R.id.combo_base_basal_rate);
        tempBasalText = view.findViewById(R.id.combo_temp_basal);
        bolusCount = view.findViewById(R.id.combo_bolus_count);
        tbrCount = view.findViewById(R.id.combo_tbr_count);

        refreshButton = view.findViewById(R.id.combo_refresh_button);
        refreshButton.setOnClickListener(this);

        return view;
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        disposable.add(RxBus.INSTANCE
                .toObservable(EventComboPumpUpdateGUI.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> updateGui(), FabricPrivacy::logException)
        );
        disposable.add(RxBus.INSTANCE
                .toObservable(EventQueueChanged.class)
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

    private void runOnUiThread(Runnable action) {
        Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(action);
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.combo_refresh_button:
                refreshButton.setEnabled(false);
                ConfigBuilderPlugin.getPlugin().getCommandQueue().readStatus("User request", new Callback() {
                    @Override
                    public void run() {
                        runOnUiThread(() -> refreshButton.setEnabled(true));
                    }
                });
                break;
        }
    }

    public void updateGui() {
        ComboPlugin plugin = ComboPlugin.getPlugin();

        // state
        stateView.setText(plugin.getStateSummary());
        PumpState ps = plugin.getPump().state;
        if (ps.insulinState == PumpState.EMPTY || ps.batteryState == PumpState.EMPTY
                || ps.activeAlert != null && ps.activeAlert.errorCode != null) {
            stateView.setTextColor(Color.RED);
            stateView.setTypeface(null, Typeface.BOLD);
        } else if (plugin.getPump().state.suspended
                || ps.activeAlert != null && ps.activeAlert.warningCode != null) {
            stateView.setTextColor(Color.YELLOW);
            stateView.setTypeface(null, Typeface.BOLD);
        } else {
            stateView.setTextColor(Color.WHITE);
            stateView.setTypeface(null, Typeface.NORMAL);
        }

        // activity
        String activity = plugin.getPump().activity;
        if (activity != null) {
            activityView.setTextColor(Color.WHITE);
            activityView.setTextSize(14);
            activityView.setText(activity);
        } else if (ConfigBuilderPlugin.getPlugin().getCommandQueue().size() > 0) {
            activityView.setTextColor(Color.WHITE);
            activityView.setTextSize(14);
            activityView.setText("");
        } else if (plugin.isInitialized()) {
            activityView.setTextColor(Color.WHITE);
            activityView.setTextSize(20);
            activityView.setText("{fa-bed}");
        } else {
            activityView.setTextColor(Color.RED);
            activityView.setTextSize(14);
            activityView.setText(MainApp.gs(R.string.pump_unreachable));
        }

        if (plugin.isInitialized()) {
            // battery
            batteryView.setTextSize(20);
            if (ps.batteryState == PumpState.EMPTY) {
                batteryView.setText("{fa-battery-empty}");
                batteryView.setTextColor(Color.RED);
            } else if (ps.batteryState == PumpState.LOW) {
                batteryView.setText("{fa-battery-quarter}");
                batteryView.setTextColor(Color.YELLOW);
            } else {
                batteryView.setText("{fa-battery-full}");
                batteryView.setTextColor(Color.WHITE);
            }

            // reservoir
            int reservoirLevel = plugin.getPump().reservoirLevel;
            if (reservoirLevel != -1) {
                reservoirView.setText(reservoirLevel + " " + MainApp.gs(R.string.insulin_unit_shortname));
            } else if (ps.insulinState == PumpState.LOW) {
                reservoirView.setText(MainApp.gs(R.string.combo_reservoir_low));
            } else if (ps.insulinState == PumpState.EMPTY) {
                reservoirView.setText(MainApp.gs(R.string.combo_reservoir_empty));
            } else {
                reservoirView.setText(MainApp.gs(R.string.combo_reservoir_normal));
            }

            if (ps.insulinState == PumpState.UNKNOWN) {
                reservoirView.setTextColor(Color.WHITE);
                reservoirView.setTypeface(null, Typeface.NORMAL);
            } else if (ps.insulinState == PumpState.LOW) {
                reservoirView.setTextColor(Color.YELLOW);
                reservoirView.setTypeface(null, Typeface.BOLD);
            } else if (ps.insulinState == PumpState.EMPTY) {
                reservoirView.setTextColor(Color.RED);
                reservoirView.setTypeface(null, Typeface.BOLD);
            } else {
                reservoirView.setTextColor(Color.WHITE);
                reservoirView.setTypeface(null, Typeface.NORMAL);
            }

            // last connection
            String minAgo = DateUtil.minAgo(plugin.getPump().lastSuccessfulCmdTime);
            long min = (System.currentTimeMillis() - plugin.getPump().lastSuccessfulCmdTime) / 1000 / 60;
            if (plugin.getPump().lastSuccessfulCmdTime + 60 * 1000 > System.currentTimeMillis()) {
                lastConnectionView.setText(R.string.combo_pump_connected_now);
                lastConnectionView.setTextColor(Color.WHITE);
            } else if (plugin.getPump().lastSuccessfulCmdTime + 30 * 60 * 1000 < System.currentTimeMillis()) {
                lastConnectionView.setText(MainApp.gs(R.string.combo_no_pump_connection, min));
                lastConnectionView.setTextColor(Color.RED);
            } else {
                lastConnectionView.setText(minAgo);
                lastConnectionView.setTextColor(Color.WHITE);
            }

            // last bolus
            Bolus bolus = plugin.getPump().lastBolus;
            if (bolus != null) {
                long agoMsc = System.currentTimeMillis() - bolus.timestamp;
                double bolusMinAgo = agoMsc / 60d / 1000d;
                String unit = MainApp.gs(R.string.insulin_unit_shortname);
                String ago;
                if ((agoMsc < 60 * 1000)) {
                    ago = MainApp.gs(R.string.combo_pump_connected_now);
                } else if (bolusMinAgo < 60) {
                    ago = DateUtil.minAgo(bolus.timestamp);
                } else {
                    ago = DateUtil.hourAgo(bolus.timestamp);
                }
                lastBolusView.setText(MainApp.gs(R.string.combo_last_bolus, bolus.amount, unit, ago));
            } else {
                lastBolusView.setText("");
            }

            // base basal rate
            baseBasalRate.setText(MainApp.gs(R.string.pump_basebasalrate, plugin.getBaseBasalRate()));

            // TBR
            String tbrStr = "";
            if (ps.tbrPercent != -1 && ps.tbrPercent != 100) {
                long minSinceRead = (System.currentTimeMillis() - plugin.getPump().state.timestamp) / 1000 / 60;
                long remaining = ps.tbrRemainingDuration - minSinceRead;
                if (remaining >= 0) {
                    tbrStr = MainApp.gs(R.string.combo_tbr_remaining, ps.tbrPercent, remaining);
                }
            }
            tempBasalText.setText(tbrStr);

            // stats
            bolusCount.setText(String.valueOf(SP.getLong(ComboPlugin.COMBO_BOLUSES_DELIVERED, 0L)));
            tbrCount.setText(String.valueOf(SP.getLong(ComboPlugin.COMBO_TBRS_SET, 0L)));
        }
    }
}