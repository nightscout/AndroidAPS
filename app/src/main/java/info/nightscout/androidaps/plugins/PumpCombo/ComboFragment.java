package info.nightscout.androidaps.plugins.PumpCombo;


import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import org.apache.commons.lang3.StringUtils;

import info.nightscout.androidaps.plugins.PumpCombo.ruffyscripter.PumpState;
import info.nightscout.androidaps.plugins.PumpCombo.ruffyscripter.history.Bolus;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.Common.SubscriberFragment;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.PumpCombo.events.EventComboPumpUpdateGUI;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.queue.events.EventQueueChanged;
import info.nightscout.utils.DateUtil;

public class ComboFragment extends SubscriberFragment implements View.OnClickListener, View.OnLongClickListener {
    private TextView stateView;
    private TextView activityView;
    private TextView batteryView;
    private TextView reservoirView;
    private TextView lastConnectionView;
    private TextView lastBolusView;
    private TextView baseBasalRate;
    private TextView tempBasalText;
    private Button refreshButton;
    private Button alertsButton;
    private Button tddsButton;
    private Button fullHistoryButton;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.combopump_fragment, container, false);

        stateView = (TextView) view.findViewById(R.id.combo_state);
        activityView = (TextView) view.findViewById(R.id.combo_activity);
        batteryView = (TextView) view.findViewById(R.id.combo_pumpstate_battery);
        reservoirView = (TextView) view.findViewById(R.id.combo_insulinstate);
        lastBolusView = (TextView) view.findViewById(R.id.combo_last_bolus);
        lastConnectionView = (TextView) view.findViewById(R.id.combo_lastconnection);
        baseBasalRate = (TextView) view.findViewById(R.id.combo_base_basal_rate);
        tempBasalText = (TextView) view.findViewById(R.id.combo_temp_basal);

        refreshButton = (Button) view.findViewById(R.id.combo_refresh_button);
        refreshButton.setOnClickListener(this);

        alertsButton = (Button) view.findViewById(R.id.combo_alerts_button);
        alertsButton.setOnClickListener(this);
        alertsButton.setOnLongClickListener(this);

        tddsButton = (Button) view.findViewById(R.id.combo_tdds_button);
        tddsButton.setOnClickListener(this);
        tddsButton.setOnLongClickListener(this);

        fullHistoryButton = (Button) view.findViewById(R.id.combo_full_history_button);
        fullHistoryButton.setOnClickListener(this);
        fullHistoryButton.setOnLongClickListener(this);

        updateGUI();
        return view;
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
                ConfigBuilderPlugin.getCommandQueue().readStatus("User request", new Callback() {
                    @Override
                    public void run() {
                        runOnUiThread(() -> refreshButton.setEnabled(true));
                    }
                });
                break;
            case R.id.combo_alerts_button:
                ComboAlertHistoryDialog ehd = new ComboAlertHistoryDialog();
                ehd.show(getFragmentManager(), ComboAlertHistoryDialog.class.getSimpleName());
                break;
            case R.id.combo_tdds_button:
                ComboTddHistoryDialog thd = new ComboTddHistoryDialog();
                thd.show(getFragmentManager(), ComboTddHistoryDialog.class.getSimpleName());
                break;
            case R.id.combo_full_history_button:
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setMessage(R.string.combo_read_full_history_info);
                builder.show();
                break;
        }
    }

    // TODO clean up when when queuing
    @Override
    public boolean onLongClick(View view) {
        switch (view.getId()) {
            case R.id.combo_alerts_button:
                alertsButton.setEnabled(false);
                tddsButton.setEnabled(false);
                fullHistoryButton.setEnabled(false);
                new Thread(() -> ComboPlugin.getPlugin().readAlertData(new Callback() {
                    @Override
                    public void run() {
                        runOnUiThread(() -> {
                            alertsButton.setEnabled(true);
                            tddsButton.setEnabled(true);
                            fullHistoryButton.setEnabled(true);
                        });
                    }
                })).start();
                return true;
            case R.id.combo_tdds_button:
                alertsButton.setEnabled(false);
                tddsButton.setEnabled(false);
                fullHistoryButton.setEnabled(false);
                new Thread(() -> ComboPlugin.getPlugin().readTddData(new Callback() {
                    @Override
                    public void run() {
                        runOnUiThread(() -> {
                            alertsButton.setEnabled(true);
                            tddsButton.setEnabled(true);
                            fullHistoryButton.setEnabled(true);
                        });
                    }
                })).start();
                return true;
            case R.id.combo_full_history_button:
                alertsButton.setEnabled(false);
                tddsButton.setEnabled(false);
                fullHistoryButton.setEnabled(false);
                new Thread(() -> ComboPlugin.getPlugin().readAllPumpData(new Callback() {
                    @Override
                    public void run() {
                        runOnUiThread(() -> {
                            alertsButton.setEnabled(true);
                            tddsButton.setEnabled(true);
                            fullHistoryButton.setEnabled(true);
                        });
                    }
                })).start();
                return true;
        }
        return false;
    }

    @Subscribe
    public void onStatusEvent(final EventComboPumpUpdateGUI ignored) {
        updateGUI();
    }

    @Subscribe
    public void onStatusEvent(final EventQueueChanged ignored) {
        updateGUI();
    }


    public void updateGUI() {
        runOnUiThread(() -> {
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
            if (StringUtils.isNotEmpty(activity)) {
                activityView.setTextSize(14);
                activityView.setText(activity);
            } else {
                activityView.setTextSize(18);
                activityView.setText("{fa-bed}");
            }

            if (plugin.isInitialized()) {
                refreshButton.setVisibility(View.VISIBLE);
                alertsButton.setVisibility(View.VISIBLE);
                tddsButton.setVisibility(View.VISIBLE);
                fullHistoryButton.setVisibility(View.VISIBLE);

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
                    reservoirView.setText(reservoirLevel + " " + MainApp.sResources.getString(R.string.treatments_wizard_unit_label));
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
                if (bolus != null && bolus.timestamp + 6 * 60 * 60 * 1000 >= System.currentTimeMillis()) {
                    long agoMsc = System.currentTimeMillis() - bolus.timestamp;
                    double bolusMinAgo = agoMsc / 60d / 1000d;
                    String unit = MainApp.gs(R.string.treatments_wizard_unit_label);
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
            }
        });
    }
}