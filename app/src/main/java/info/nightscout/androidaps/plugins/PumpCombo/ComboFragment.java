package info.nightscout.androidaps.plugins.PumpCombo;


import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import de.jotomo.ruffy.spi.CommandResult;
import de.jotomo.ruffy.spi.PumpState;
import de.jotomo.ruffy.spi.history.Bolus;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.TemporaryBasal;
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
    private TextView basaBasalRateView;
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
        lastBolusView = (TextView) view.findViewById(R.id.combo_lastbolus);
        basaBasalRateView = (TextView) view.findViewById(R.id.combo_basabasalrate);
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
/*                Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText("Refreshing");
                        }
                    });
                }*/
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        ComboPlugin.getPlugin().refreshDataFromPump("User request");
                    }
                });
                thread.start();
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
                    if (plugin.getPump().lastCmdResult == null) {
                        statusView.setText("Initializing");
                    } else {
                        statusView.setText(plugin.getPump().state.getStateSummary());
                    }
                    if (plugin.getPump().state.errorMsg != null) {
                        statusView.setTextColor(Color.RED);
                    } else {
                        statusView.setTextColor(Color.WHITE);
                    }
                    // ???
                    if (plugin.isInitialized()) {
                        PumpState ps = plugin.getPump().state;
                        if (ps != null) {
                            boolean tbrActive = ps.tbrPercent != -1 && ps.tbrPercent != 100;
                            if (tbrActive) {
//                                tbrPercentageText.setText("" + ps.tbrPercent + "%");
//                                tbrDurationText.setText("" + ps.tbrRemainingDuration + " min");
//                                tbrRateText.setText("" + ps.tbrRate + " U/h");
                            } else {
//                                tbrPercentageText.setText("Default basal rate running");
//                                tbrDurationText.setText("");
//                                tbrRateText.setText("");
                            }
//                            pumpErrorText.setText(ps.errorMsg != null ? ps.errorMsg : "");
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
                            switch (ps.insulinState) {
                                case 0:
                                    reservoirView.setText("ok");
                                    break;
                                case 1:
                                    reservoirView.setText("low");
                                    break;
                                case 2:
                                    reservoirView.setText("empty");
                                    break;
                            }
                            int reservoirLevel = plugin.getPump().history.reservoirLevel;
                            reservoirView.setText(reservoirLevel == -1 ? "" : "" + reservoirLevel + " U");
                        }

                        if (plugin.getPump().lastCmdResult != null) {
                            CommandResult lastCmdResult = plugin.getPump().lastCmdResult;
                            lastConnectionView.setText(
                                    "4 m ago (18:58)"
//                                    new Date(lastCmdResult.completionTime).toLocaleString()
                            );
                        }

                        plugin.getPump().history.bolusHistory.add(new Bolus(System.currentTimeMillis() - 7 * 60 * 1000, 12.8d));
                        if (!plugin.getPump().history.bolusHistory.isEmpty()) {
                            Bolus bolus = plugin.getPump().history.bolusHistory.get(0);
//                            double agoHours = agoMsec / 60d / 60d / 1000d;
//                            if (agoHours < 6) // max 6h back
                            if (bolus.timestamp + 6 * 60 * 60 * 1000 < System.currentTimeMillis()) {
                                lastBolusView.setText("");
                            } else {
                                // TODO only if !SMB; also: bolus history: should only be used to sync to DB;
                                // remember that datum someplace else?
                                long agoMsc = System.currentTimeMillis() - bolus.timestamp;
                                double agoHours = agoMsc / 60d / 60d / 1000d;
                                lastBolusView.setText(DateUtil.timeString(bolus.timestamp) +
                                        " (" + DecimalFormatter.to1Decimal(agoHours) + " " + MainApp.sResources.getString(R.string.hoursago) + ") " +
                                        DecimalFormatter.to2Decimal(bolus.amount) + " U");
                                lastBolusView.setText("12.80 U (15 m ago, 19:04)"); // (19:04)");
                            }
                        }

                        basaBasalRateView.setText(DecimalFormatter.to2Decimal(plugin.getBaseBasalRate()) + " U/h");

                        TemporaryBasal temporaryBasal = new TemporaryBasal(System.currentTimeMillis());
                        temporaryBasal.percentRate = 420;
                        temporaryBasal.durationInMinutes = 20;

                        tempBasalText.setText(temporaryBasal.toStringFull());
                        tempBasalText.setText("420% 5/20' (18:45)");

                        CommandResult lastCmdResult1 = plugin.getPump().lastCmdResult;
                        String lastCmd = lastCmdResult1.request;
                        if (lastCmd != null) {
//                            lastCmdText.setText(lastCmd);
//                            lastCmdTimeText.setText(plugin.getPump().lastCmdTime.toLocaleString());
                        } else {
//                            lastCmdText.setText("");
//                            lastCmdTimeText.setText("");
                        }

                        if (lastCmdResult1.message != null) {
//                            lastCmdResultText.setText(lastCmdResult1.message);
//                            lastCmdDurationText.setText(lastCmdResult1.duration);
                        } else {
//                            lastCmdResultText.setText("");
//                            lastCmdDurationText.setText("");
                        }
                    }
                }
            });
    }
}
