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

import de.jotomo.ruffy.spi.CommandResult;
import de.jotomo.ruffy.spi.PumpState;
import de.jotomo.ruffy.spi.history.Bolus;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.Common.SubscriberFragment;
import info.nightscout.androidaps.plugins.PumpCombo.events.EventComboPumpUpdateGUI;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.DecimalFormatter;

public class ComboFragment extends SubscriberFragment implements View.OnClickListener {
    private static Logger log = LoggerFactory.getLogger(ComboFragment.class);

    private TextView statusView;
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

        statusView = (TextView) view.findViewById(R.id.combo_status);
        batteryView = (TextView) view.findViewById(R.id.combo_pumpstate_battery);
        reservoirView = (TextView) view.findViewById(R.id.combo_insulinstate);
        lastConnectionView = (TextView) view.findViewById(R.id.combo_lastconnection);
        lastBolusView = (TextView) view.findViewById(R.id.combo_last_bolus);
        tempBasalText = (TextView) view.findViewById(R.id.combo_temp_basal);

        refresh = (Button) view.findViewById(R.id.combo_refresh);
        refresh.setOnClickListener(this);

        updateGUI();
        return view;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.combo_refresh:
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        ComboPlugin.getPlugin().refreshDataFromPump("User request");
                    }
                });
                thread.start();
                break;
            case R.id.combo_history:
                // TODO show popup with warnings/errors from the pump
                break;
            case R.id.combo_stats:
                // TODO show TDD stats from the pump
                break;
        }
    }

    @Subscribe
    public void onStatusEvent(final EventComboPumpUpdateGUI ev) {
        if (ev.status != null) {
            Activity activity = getActivity();
            if (activity != null)
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        statusView.setText(ev.status);
                        statusView.setTextColor(Color.WHITE);
                    }
                });
        } else {
            updateGUI();
        }
    }

    public void updateGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ComboPlugin plugin = ComboPlugin.getPlugin();
                    if (plugin.isInitialized()) {
                        // status
                        statusView.setText(plugin.getPump().state.getStateSummary());
                        if (plugin.getPump().state.errorMsg != null) {
                            statusView.setTextColor(Color.RED);
                        } else if (plugin.getPump().state.suspended) {
                            statusView.setTextColor(Color.YELLOW);
                        } else {
                            statusView.setTextColor(Color.WHITE);
                        }

                        // battery
                        PumpState ps = plugin.getPump().state;
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
                        CommandResult lastCmdResult = plugin.getPump().lastCmdResult;
                        if (lastCmdResult != null) {
                            String minAgo = DateUtil.minAgo(lastCmdResult.completionTime);
                            String time = DateUtil.timeString(lastCmdResult.completionTime);
                            lastConnectionView.setText("" + minAgo + " (" + time + ")");

                            // last bolus
                            plugin.getPump().history.bolusHistory.add(new Bolus(System.currentTimeMillis() - 7 * 60 * 1000, 12.8d));
                            Bolus bolus = plugin.getPump().lastBolus;
                            if (bolus == null || bolus.timestamp + 6 * 60 * 60 * 1000 < System.currentTimeMillis()) {
                                lastBolusView.setText("");
                            } else {
                                long agoMsc = System.currentTimeMillis() - bolus.timestamp;
                                double agoHours = agoMsc / 60d / 60d / 1000d;
                                lastBolusView.setText(DecimalFormatter.to2Decimal(bolus.amount) + " U " +
                                        "(" + DecimalFormatter.to1Decimal(agoHours) + " " + MainApp.sResources.getString(R.string.hoursago) + ", "
                                        + DateUtil.timeString(bolus.timestamp) + ") ");
                            }

                            // TBR
                            boolean tbrActive = ps.tbrPercent != -1 && ps.tbrPercent != 100;
                            String tbrStr = "";
                            if (tbrActive) {
                                long minSinceRead = (System.currentTimeMillis() - lastCmdResult.completionTime) / 1000 / 60;
                                long remaining = ps.tbrRemainingDuration - minSinceRead;
                                if (remaining >= 0) {
                                    tbrStr = ps.tbrPercent + "% (" + remaining + " min remaining)";
                                }
                            }
                            tempBasalText.setText(tbrStr);
                        }
                    }
                }
            });
    }
}
