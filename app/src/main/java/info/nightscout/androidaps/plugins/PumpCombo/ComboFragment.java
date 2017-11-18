package info.nightscout.androidaps.plugins.PumpCombo;


import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import de.jotomo.ruffy.spi.PumpState;
import de.jotomo.ruffy.spi.history.Bolus;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.Common.SubscriberFragment;
import info.nightscout.androidaps.plugins.PumpCombo.events.EventComboPumpUpdateGUI;
import info.nightscout.utils.DateUtil;

public class ComboFragment extends SubscriberFragment implements View.OnClickListener, View.OnLongClickListener {
    private TextView stateView;
    private TextView activityView;
    private TextView batteryView;
    private TextView reservoirView;
    private TextView lastConnectionView;
    private TextView lastBolusView;
    private TextView tempBasalText;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.combopump_fragment, container, false);

        stateView = (TextView) view.findViewById(R.id.combo_state);
        activityView = (TextView) view.findViewById(R.id.combo_activity);
        batteryView = (TextView) view.findViewById(R.id.combo_pumpstate_battery);
        reservoirView = (TextView) view.findViewById(R.id.combo_insulinstate);
        lastConnectionView = (TextView) view.findViewById(R.id.combo_lastconnection);
        lastBolusView = (TextView) view.findViewById(R.id.combo_last_bolus);
        tempBasalText = (TextView) view.findViewById(R.id.combo_temp_basal);

        Button refresh = (Button) view.findViewById(R.id.combo_refresh);
        refresh.setOnClickListener(this);
        refresh.setOnLongClickListener(this);

        Button errorHistory = (Button) view.findViewById(R.id.combo_error_history);
        errorHistory.setOnClickListener(this);

        Button tddHistory = (Button) view.findViewById(R.id.combo_tdd_history);
        tddHistory.setOnClickListener(this);

        updateGUI();
        return view;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.combo_refresh:
                new Thread(() -> ComboPlugin.getPlugin().refreshDataFromPump("User request")).start();
                break;
            case R.id.combo_error_history:
                ComboErrorHistoryDialog ehd = new ComboErrorHistoryDialog();
                ehd.show(getFragmentManager(), ComboErrorHistoryDialog.class.getSimpleName());
                break;
            case R.id.combo_tdd_history:
                ComboTddHistoryDialog thd = new ComboTddHistoryDialog();
                thd.show(getFragmentManager(), ComboTddHistoryDialog.class.getSimpleName());
                break;
        }
    }

    @Override
    public boolean onLongClick(View view) {
        switch (view.getId()) {
            case R.id.combo_refresh:
                new Thread(() -> ComboPlugin.getPlugin().forceFullHistoryRead()).start();
                return true;
        }
        return false;
    }

    @Subscribe
    public void onStatusEvent(final EventComboPumpUpdateGUI ev) {
        updateGUI();
    }

    public void updateGUI() {
        Activity fragmentActivity = getActivity();
        if (fragmentActivity != null)
            fragmentActivity.runOnUiThread(() -> {
                ComboPlugin plugin = ComboPlugin.getPlugin();

                // state
                stateView.setText(plugin.getStateSummary());
                PumpState ps = plugin.getPump().state;
                if (ps.insulinState == PumpState.EMPTY || ps.batteryState == PumpState.EMPTY) {
                    stateView.setTextColor(Color.RED);
                } else if (plugin.getPump().state.suspended) {
                    stateView.setTextColor(Color.YELLOW);
                } else {
                    stateView.setTextColor(Color.WHITE);
                }

                // activity
                String activity = plugin.getPump().activity;
                activityView.setText(activity != null ? activity : "");

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
                    reservoirView.setText(reservoirLevel == -1 ? "" : "" + reservoirLevel + " U");
                    if (ps.insulinState == PumpState.LOW) {
                        reservoirView.setTextColor(Color.YELLOW);
                    } else if (ps.insulinState == PumpState.EMPTY) {
                        reservoirView.setTextColor(Color.RED);
                    } else {
                        reservoirView.setTextColor(Color.WHITE);
                    }

                    // last connection
                    String minAgo = DateUtil.minAgo(plugin.getPump().lastSuccessfulCmdTime);
                    long min = (System.currentTimeMillis() - plugin.getPump().lastSuccessfulCmdTime) / 1000 / 60;
                    if (plugin.getPump().lastSuccessfulCmdTime + 60 * 1000 > System.currentTimeMillis()) {
                        lastConnectionView.setText(R.string.combo_pump_connected_now);
                        lastConnectionView.setTextColor(Color.WHITE);
                    } else if (plugin.getPump().lastSuccessfulCmdTime + 30 * 60 * 1000 < System.currentTimeMillis()) {
                        lastConnectionView.setText(getString(R.string.combo_no_pump_connection, min));
                        lastConnectionView.setTextColor(Color.RED);
                    } else if (plugin.getPump().lastCmdTime > plugin.getPump().lastSuccessfulCmdTime) {
                        String lastFailed = minAgo + "\n" + getString(R.string.combo_connect_attempt_failed);
                        lastConnectionView.setText(lastFailed);
                        lastConnectionView.setTextColor(Color.YELLOW);
                    } else {
                        lastConnectionView.setText(minAgo);
                        lastConnectionView.setTextColor(Color.WHITE);
                    }

                    // last bolus
                    Bolus bolus = plugin.getPump().lastBolus;
                    if (bolus != null && bolus.timestamp + 6 * 60 * 60 * 1000 >= System.currentTimeMillis()) {
                        long agoMsc = System.currentTimeMillis() - bolus.timestamp;
                        double bolusMinAgo = agoMsc / 60d / 1000d;
                        double bolusHoursAgo = agoMsc / 60d / 60d / 1000d;
                        // TODO i18n
                        if ((agoMsc < 60 * 1000)) {
                            lastBolusView.setText(String.format("%.1f U (now)", bolus.amount, (int) bolusMinAgo));
                        } else if (bolusMinAgo < 60) {
                            lastBolusView.setText(String.format("%.1f U (%d min ago)", bolus.amount, (int) bolusMinAgo));
//                            lastBolusView.setText(getString(R.string.combo_last_bolus, bolus.amount,
//                                    getString(R.string.minago, bolusMinAgo), DateUtil.timeString(bolus.timestamp)));
                        } else {
                            lastBolusView.setText(String.format("%.1f U (%.1f h ago)", bolus.amount, bolusHoursAgo));
//                            lastBolusView.setText(getString(R.string.combo_last_bolus, bolus.amount,
//                                    String.format("%.1f", bolusHoursAgo) + getString(R.string.hoursago), DateUtil.timeString(bolus.timestamp)));
                        }
                    } else {
                        lastBolusView.setText("");
                    }

                    // TBR
                    String tbrStr = "";
                    if (ps.tbrPercent != -1 && ps.tbrPercent != 100) {
                        long minSinceRead = (System.currentTimeMillis() - plugin.getPump().state.timestamp) / 1000 / 60;
                        long remaining = ps.tbrRemainingDuration - minSinceRead;
                        if (remaining >= 0) {
                            tbrStr = getString(R.string.combo_tbr_remaining, ps.tbrPercent, remaining);
                        }
                    }
                    tempBasalText.setText(tbrStr);
                }
            });
    }
}
