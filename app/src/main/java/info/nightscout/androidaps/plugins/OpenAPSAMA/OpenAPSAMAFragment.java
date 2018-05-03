package info.nightscout.androidaps.plugins.OpenAPSAMA;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.crashlytics.android.answers.CustomEvent;
import com.squareup.otto.Subscribe;

import org.json.JSONArray;
import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.Common.SubscriberFragment;
import info.nightscout.androidaps.plugins.OpenAPSMA.events.EventOpenAPSUpdateGui;
import info.nightscout.androidaps.plugins.OpenAPSMA.events.EventOpenAPSUpdateResultGui;
import info.nightscout.utils.FabricPrivacy;
import info.nightscout.utils.JSONFormatter;

public class OpenAPSAMAFragment extends SubscriberFragment implements View.OnClickListener {
    private static Logger log = LoggerFactory.getLogger(OpenAPSAMAFragment.class);

    Button run;
    TextView lastRunView;
    TextView glucoseStatusView;
    TextView currentTempView;
    TextView iobDataView;
    TextView profileView;
    TextView mealDataView;
    TextView autosensDataView;
    TextView resultView;
    TextView scriptdebugView;
    TextView requestView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.openapsama_fragment, container, false);

            run = (Button) view.findViewById(R.id.openapsma_run);
            run.setOnClickListener(this);
            lastRunView = (TextView) view.findViewById(R.id.openapsma_lastrun);
            glucoseStatusView = (TextView) view.findViewById(R.id.openapsma_glucosestatus);
            currentTempView = (TextView) view.findViewById(R.id.openapsma_currenttemp);
            iobDataView = (TextView) view.findViewById(R.id.openapsma_iobdata);
            profileView = (TextView) view.findViewById(R.id.openapsma_profile);
            mealDataView = (TextView) view.findViewById(R.id.openapsma_mealdata);
            autosensDataView = (TextView) view.findViewById(R.id.openapsma_autosensdata);
            scriptdebugView = (TextView) view.findViewById(R.id.openapsma_scriptdebugdata);
            resultView = (TextView) view.findViewById(R.id.openapsma_result);
            requestView = (TextView) view.findViewById(R.id.openapsma_request);

            updateGUI();
            return view;
        } catch (Exception e) {
            FabricPrivacy.logException(e);
        }

        return null;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.openapsma_run:
                OpenAPSAMAPlugin.getPlugin().invoke("OpenAPSAMA button", false);
                FabricPrivacy.getInstance().logCustom(new CustomEvent("OpenAPS_AMA_Run"));
                break;
        }

    }

    @Subscribe
    public void onStatusEvent(final EventOpenAPSUpdateGui ev) {
        updateGUI();
    }

    @Subscribe
    public void onStatusEvent(final EventOpenAPSUpdateResultGui ev) {
        updateResultGUI(ev.text);
    }

    @Override
    protected void updateGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    DetermineBasalResultAMA lastAPSResult = OpenAPSAMAPlugin.getPlugin().lastAPSResult;
                    if (lastAPSResult != null) {
                        resultView.setText(JSONFormatter.format(lastAPSResult.json));
                        requestView.setText(lastAPSResult.toSpanned());
                    }
                    DetermineBasalAdapterAMAJS determineBasalAdapterAMAJS = OpenAPSAMAPlugin.getPlugin().lastDetermineBasalAdapterAMAJS;
                    if (determineBasalAdapterAMAJS != null) {
                        glucoseStatusView.setText(JSONFormatter.format(determineBasalAdapterAMAJS.getGlucoseStatusParam()));
                        currentTempView.setText(JSONFormatter.format(determineBasalAdapterAMAJS.getCurrentTempParam()));
                        try {
                            JSONArray iobArray = new JSONArray(determineBasalAdapterAMAJS.getIobDataParam());
                            iobDataView.setText(String.format(MainApp.gs(R.string.array_of_elements), iobArray.length()) + "\n" + JSONFormatter.format(iobArray.getString(0)));
                        } catch (JSONException e) {
                            log.error("Unhandled exception", e);
                            iobDataView.setText("JSONException");
                        }
                        profileView.setText(JSONFormatter.format(determineBasalAdapterAMAJS.getProfileParam()));
                        mealDataView.setText(JSONFormatter.format(determineBasalAdapterAMAJS.getMealDataParam()));
                        scriptdebugView.setText(determineBasalAdapterAMAJS.getScriptDebug());
                    }
                    if (OpenAPSAMAPlugin.getPlugin().lastAPSRun != null) {
                        lastRunView.setText(OpenAPSAMAPlugin.getPlugin().lastAPSRun.toLocaleString());
                    }
                    if (OpenAPSAMAPlugin.getPlugin().lastAutosensResult != null) {
                        autosensDataView.setText(JSONFormatter.format(OpenAPSAMAPlugin.getPlugin().lastAutosensResult.json()));
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
                    autosensDataView.setText("");
                    scriptdebugView.setText("");
                    requestView.setText("");
                    lastRunView.setText("");
                }
            });
    }
}
