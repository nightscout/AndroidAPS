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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.jotomo.ruffy.spi.PumpState;
import de.jotomo.ruffy.spi.history.Bolus;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.Common.SubscriberFragment;
import info.nightscout.androidaps.plugins.PumpCombo.events.EventComboPumpUpdateGUI;
import info.nightscout.utils.DateUtil;

public class ComboFragment extends SubscriberFragment implements View.OnClickListener, View.OnLongClickListener {
    private static Logger log = LoggerFactory.getLogger(ComboFragment.class);

    private TextView stateView;
    private TextView activityView;
    private TextView batteryView;
    private TextView reservoirView;
    private TextView lastConnectionView;
    private TextView lastBolusView;
    private TextView tempBasalText;

    private Button refresh;

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

        refresh = (Button) view.findViewById(R.id.combo_refresh);
        refresh.setOnClickListener(this);
        refresh.setOnLongClickListener(this);

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
                // TODO v2 show popup with pump errors and comm problems steal code from profile view, called on overview
                break;
        }
    }

    @Override
    public boolean onLongClick(View view) {
        switch (view.getId()) {
            case R.id.combo_refresh:
                new Thread(() -> ComboPlugin.getPlugin().forceSyncFullHistory()).start();
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
                activityView.setText(activity != null ? activity : getString(R.string.combo_pump_action_idle));

                if (plugin.isInitialized()) {
                    // battery
                    if (ps.batteryState == PumpState.EMPTY) {
                        batteryView.setText("{fa-battery-empty}");
                        batteryView.setTextColor(Color.RED);
                    } else if (ps.batteryState == PumpState.LOW) {
                        batteryView.setText("{fa-battery-quarter}");
                        batteryView.setTextColor(Color.YELLOW);
                    } else if (ps.batteryState == PumpState.UNKNOWN) {
                        batteryView.setText("");
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
                    String minAgo = DateUtil.minAgo(plugin.getPump().lastSuccessfulConnection);
                    String time = DateUtil.timeString(plugin.getPump().lastSuccessfulConnection);
                    String timeAgo = getString(R.string.combo_last_connection_time, minAgo, time);
                    if (plugin.getPump().lastSuccessfulConnection == 0) {
                        lastConnectionView.setText(R.string.combo_pump_never_connected);
                        lastConnectionView.setTextColor(Color.RED);
                    } else if (plugin.getPump().lastSuccessfulConnection < System.currentTimeMillis() - 30 * 60 * 1000) {
                        lastConnectionView.setText(getString(R.string.combo_no_pump_connection, minAgo));
                        lastConnectionView.setTextColor(Color.RED);
                    } else if (plugin.getPump().lastConnectionAttempt > plugin.getPump().lastSuccessfulConnection) {
                        lastConnectionView.setText(timeAgo + "\n" + getString(R.string.combo_connect_attempt_failed));
                        lastConnectionView.setTextColor(Color.YELLOW);
                    } else {
                        lastConnectionView.setText(timeAgo);
                        lastConnectionView.setTextColor(Color.WHITE);
                    }

                    // last bolus
                    Bolus bolus = plugin.getPump().lastBolus;
                    if (bolus != null && bolus.timestamp + 6 * 60 * 60 * 1000 >= System.currentTimeMillis()) {
                        long agoMsc = System.currentTimeMillis() - bolus.timestamp;
                        double agoHours = agoMsc / 60d / 60d / 1000d;
                        lastBolusView.setText(getString(R.string.combo_last_bolus,
                                bolus.amount,
                                agoHours,
                                getString(R.string.hoursago),
                                DateUtil.timeString(bolus.timestamp)));
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
