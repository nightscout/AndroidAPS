package info.nightscout.androidaps.plugins.OpenAPSMA;

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

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.FragmentBase;
import info.nightscout.androidaps.plugins.OpenAPSMA.events.EventOpenAPSMAUpdateGui;
import info.nightscout.androidaps.plugins.OpenAPSMA.events.EventOpenAPSMAUpdateResultGui;
import info.nightscout.utils.JSONFormatter;

public class OpenAPSMAFragment extends Fragment implements View.OnClickListener, FragmentBase {
    private static Logger log = LoggerFactory.getLogger(OpenAPSMAFragment.class);

    private static OpenAPSMAPlugin openAPSMAPlugin = new OpenAPSMAPlugin();

    public static OpenAPSMAPlugin getPlugin() {
        return openAPSMAPlugin;
    }

    Button run;
    TextView lastRunView;
    TextView glucoseStatusView;
    TextView currentTempView;
    TextView iobDataView;
    TextView profileView;
    TextView mealDataView;
    TextView resultView;
    TextView requestView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.openapsma_fragment, container, false);

        run = (Button) view.findViewById(R.id.openapsma_run);
        run.setOnClickListener(this);
        lastRunView = (TextView) view.findViewById(R.id.openapsma_lastrun);
        glucoseStatusView = (TextView) view.findViewById(R.id.openapsma_glucosestatus);
        currentTempView = (TextView) view.findViewById(R.id.openapsma_currenttemp);
        iobDataView = (TextView) view.findViewById(R.id.openapsma_iobdata);
        profileView = (TextView) view.findViewById(R.id.openapsma_profile);
        mealDataView = (TextView) view.findViewById(R.id.openapsma_mealdata);
        resultView = (TextView) view.findViewById(R.id.openapsma_result);
        requestView = (TextView) view.findViewById(R.id.openapsma_request);

        updateGUI();
        return view;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.openapsma_run:
                openAPSMAPlugin.invoke();
                break;
        }

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
    public void onStatusEvent(final EventOpenAPSMAUpdateGui ev) {
        updateGUI();
    }

    @Subscribe
    public void onStatusEvent(final EventOpenAPSMAUpdateResultGui ev) {
        updateResultGUI(ev.text);
    }

    void updateGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (openAPSMAPlugin.lastAPSResult != null) {
                        glucoseStatusView.setText(JSONFormatter.format(openAPSMAPlugin.lastDetermineBasalAdapterJS.getGlucoseStatusParam()));
                        currentTempView.setText(JSONFormatter.format(openAPSMAPlugin.lastDetermineBasalAdapterJS.getCurrentTempParam()));
                        iobDataView.setText(JSONFormatter.format(openAPSMAPlugin.lastDetermineBasalAdapterJS.getIobDataParam()));
                        profileView.setText(JSONFormatter.format(openAPSMAPlugin.lastDetermineBasalAdapterJS.getProfileParam()));
                        mealDataView.setText(JSONFormatter.format(openAPSMAPlugin.lastDetermineBasalAdapterJS.getMealDataParam()));
                        resultView.setText(JSONFormatter.format(openAPSMAPlugin.lastAPSResult.json));
                        requestView.setText(openAPSMAPlugin.lastAPSResult.toSpanned());
                        lastRunView.setText(openAPSMAPlugin.lastAPSRun.toLocaleString());
                    }
                }
            });
    }

    void updateResultGUI(final String text) {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    resultView.setText(text);
                    glucoseStatusView.setText("");
                    currentTempView.setText("");
                    iobDataView.setText("");
                    profileView.setText("");
                    mealDataView.setText("");
                    requestView.setText("");
                    lastRunView.setText("");
                }
            });
    }
}
