package info.nightscout.androidaps.plugins.PumpCombo;


import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import de.jotomo.ruffyscripter.PumpState;
import de.jotomo.ruffyscripter.commands.Command;
import de.jotomo.ruffyscripter.commands.CommandResult;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.PumpCombo.events.EventComboPumpUpdateGUI;

public class ComboFragment extends Fragment implements View.OnClickListener {
    private static Logger log = LoggerFactory.getLogger(ComboFragment.class);

    private static ComboPlugin comboPlugin = new ComboPlugin();

    public static ComboPlugin getPlugin() {
        return comboPlugin;
    }

    private Button refresh;

    private TextView statusText;

    private TextView tbrPercentageText;
    private TextView tbrDurationText;
    private TextView tbrRateText;
    private TextView pumpErrorText;

    private TextView lastCmdText;
    private TextView lastCmdTimeText;
    private TextView lastCmdResultText;
    private TextView lastCmdDurationText;

    private TextView pumpstateBatteryText;
    private TextView insulinstateText;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.combopump_fragment, container, false);

        refresh = (Button) view.findViewById(R.id.combo_refresh);

        statusText = (TextView) view.findViewById(R.id.combo_status);

        tbrPercentageText = (TextView) view.findViewById(R.id.combo_tbr_percentage);
        tbrDurationText = (TextView) view.findViewById(R.id.combo_tbr_duration);
        tbrRateText = (TextView) view.findViewById(R.id.combo_tbr_rate);
        pumpErrorText = (TextView) view.findViewById(R.id.combo_pump_error);

        lastCmdText = (TextView) view.findViewById(R.id.combo_last_command);
        lastCmdTimeText = (TextView) view.findViewById(R.id.combo_last_command_time);
        lastCmdResultText = (TextView) view.findViewById(R.id.combo_last_command_result);
        lastCmdDurationText = (TextView) view.findViewById(R.id.combo_last_command_duration);
        pumpstateBatteryText = (TextView) view.findViewById(R.id.combo_pumpstate_battery);
        insulinstateText = (TextView) view.findViewById(R.id.combo_insulinstate);

        refresh.setOnClickListener(this);

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
        updateGUI();
    }

    @Subscribe
    public void onStatusEvent(final EventComboPumpUpdateGUI ev) {
        updateGUI();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.combo_refresh:
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        getPlugin().refreshDataFromPump("User request");
                    }
                });
                thread.start();
                break;
        }
    }

    public void updateGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    statusText.setText(getPlugin().getPump().stateSummary);
                    if (getPlugin().isInitialized()) {
                        PumpState ps = getPlugin().getPump().state;
                        if (ps != null) {
                            boolean tbrActive = ps.tbrPercent != -1 && ps.tbrPercent != 100;
                            if (tbrActive) {
                                tbrPercentageText.setText("" + ps.tbrPercent + "%");
                                tbrDurationText.setText("" + ps.tbrRemainingDuration + " min");
                                tbrRateText.setText("" + ps.tbrRate + " U/h");
                            } else {
                                tbrPercentageText.setText("Default basal rate running");
                                tbrDurationText.setText("");
                                tbrRateText.setText("");
                            }
                            pumpErrorText.setText(ps.errorMsg != null ? ps.errorMsg : "");
                            if(ps.lowBattery){
                                pumpstateBatteryText.setText("{fa-battery-empty}");
                                pumpstateBatteryText.setTextColor(Color.RED);
                            } else {
                                pumpstateBatteryText.setText("{fa-battery-three-quarters}");
                                pumpstateBatteryText.setTextColor(Color.WHITE);
                            }
                            switch (ps.insulinState){
                                case 0: insulinstateText.setText("ok");
                                    insulinstateText.setTextColor(Color.WHITE);
                                    break;
                                case 1: insulinstateText.setText("low");
                                    insulinstateText.setTextColor(Color.YELLOW);
                                    break;
                                case 2: insulinstateText.setText("empty");
                                    insulinstateText.setTextColor(Color.RED);
                                    break;
                            }
                        }

                        Command lastCmd = getPlugin().getPump().lastCmd;
                        if (lastCmd != null) {
                            lastCmdText.setText(lastCmd.toString());
                            lastCmdTimeText.setText(getPlugin().getPump().lastCmdTime.toLocaleString());
                        } else {
                            lastCmdText.setText("");
                            lastCmdTimeText.setText("");
                        }

                        CommandResult lastCmdResult = getPlugin().getPump().lastCmdResult;
                        if (lastCmdResult != null && lastCmdResult.message != null) {
                            lastCmdResultText.setText(lastCmdResult.message);
                            lastCmdDurationText.setText(lastCmdResult.duration);
                        } else {
                            lastCmdResultText.setText("");
                            lastCmdDurationText.setText("");
                        }
                    }
                }
            });
    }
}
