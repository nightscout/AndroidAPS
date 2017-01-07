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
import info.nightscout.androidaps.plugins.OpenAPSMA.events.EventOpenAPSUpdateGui;
import info.nightscout.androidaps.plugins.OpenAPSMA.events.EventOpenAPSUpdateResultGui;
import info.nightscout.utils.JSONFormatter;

public class OpenAPSMAFragment extends Fragment implements View.OnClickListener, FragmentBase {
    private static Logger log = LoggerFactory.getLogger(OpenAPSMAFragment.class);

    private static OpenAPSMAPlugin openAPSMAPlugin;

    public static OpenAPSMAPlugin getPlugin() {
        if(openAPSMAPlugin==null){
            openAPSMAPlugin = new OpenAPSMAPlugin();
        }
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
                getPlugin().invoke("OpenAPSMA button");
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
    public void onStatusEvent(final EventOpenAPSUpdateGui ev) {
        updateGUI();
    }

    @Subscribe
    public void onStatusEvent(final EventOpenAPSUpdateResultGui ev) {
        updateResultGUI(ev.text);
    }

    void updateGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    DetermineBasalResultMA lastAPSResult = getPlugin().lastAPSResult;
                    if (lastAPSResult != null) {
                        resultView.setText(JSONFormatter.format(lastAPSResult.json));
                        requestView.setText(lastAPSResult.toSpanned());
                    }
                    DetermineBasalAdapterMAJS determineBasalAdapterMAJS = getPlugin().lastDetermineBasalAdapterMAJS;
                    if (determineBasalAdapterMAJS != null) {
                        glucoseStatusView.setText(JSONFormatter.format(determineBasalAdapterMAJS.getGlucoseStatusParam()));
                        currentTempView.setText(JSONFormatter.format(determineBasalAdapterMAJS.getCurrentTempParam()));
                        iobDataView.setText(JSONFormatter.format(determineBasalAdapterMAJS.getIobDataParam()));
                        profileView.setText(JSONFormatter.format(determineBasalAdapterMAJS.getProfileParam()));
                        mealDataView.setText(JSONFormatter.format(determineBasalAdapterMAJS.getMealDataParam()));
                    }
                    if (getPlugin().lastAPSRun != null) {
                        lastRunView.setText(getPlugin().lastAPSRun.toLocaleString());
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
