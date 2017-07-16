package info.nightscout.androidaps.plugins.PumpCombo;


import android.app.Activity;
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
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.PumpCombo.events.EventComboPumpUpdateGUI;

public class ComboFragment extends Fragment implements View.OnClickListener {
    private static Logger log = LoggerFactory.getLogger(ComboFragment.class);

    private static ComboPlugin comboPlugin = new ComboPlugin();

    public static ComboPlugin getPlugin() {
        return comboPlugin;
    }

    private Button update;
    private TextView status;
    private TextView tbrPercentage;
    private TextView tbrDurationRemaining;
    private TextView tbrRate;
    private TextView errorMsg;
    private TextView lastUpdate;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.combopump_fragment, container, false);

        update = (Button) view.findViewById(R.id.combo_update);
        status = (TextView) view.findViewById(R.id.combo_status);
        tbrPercentage = (TextView) view.findViewById(R.id.combo_tbr_percentage);
        tbrDurationRemaining = (TextView) view.findViewById(R.id.combo_tbr_duration_remaining);
        tbrRate = (TextView) view.findViewById(R.id.combo_tbr_rate);
        errorMsg = (TextView) view.findViewById(R.id.combo_error_message);
        lastUpdate = (TextView) view.findViewById(R.id.combo_last_update);

        update.setOnClickListener(this);
        status.setText("Initializing");

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
    public void onStatusEvent(final EventComboPumpUpdateGUI ev) {
        updateGUI();
    }


    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.combo_update:
                status.setText("Updating");
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        getPlugin().fetchPumpState();
                        updateGUI();
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
                    status.setText(getPlugin().statusSummary);
                    PumpState ps = getPlugin().pumpState;
                    boolean tbrActive = ps.tbrPercent != -1 && ps.tbrPercent != 100;
                    if (tbrActive) {
                        tbrPercentage.setText("" + ps.tbrPercent + "%");
                        tbrDurationRemaining.setText("" + ps.tbrRemainingDuration + " min");
                        tbrRate.setText("" + ps.tbrRate + " U/h");
                    } else {
                        tbrPercentage.setText("Default basal rate running");
                        tbrDurationRemaining.setText("");
                        tbrRate.setText("" + getPlugin().getBaseBasalRate() + " U/h");
                    }
                    errorMsg.setText(ps.errorMsg != null ? ps.errorMsg : "");
                    lastUpdate.setText(ps.timestamp.toLocaleString());
                }
            });
    }
}
