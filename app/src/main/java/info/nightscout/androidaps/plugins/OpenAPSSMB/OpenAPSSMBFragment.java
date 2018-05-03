package info.nightscout.androidaps.plugins.OpenAPSSMB;

import android.app.Activity;
import android.os.Bundle;
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

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.Common.SubscriberFragment;
import info.nightscout.androidaps.plugins.OpenAPSMA.events.EventOpenAPSUpdateGui;
import info.nightscout.androidaps.plugins.OpenAPSMA.events.EventOpenAPSUpdateResultGui;
import info.nightscout.utils.FabricPrivacy;
import info.nightscout.utils.JSONFormatter;

public class OpenAPSSMBFragment extends SubscriberFragment {
    private static Logger log = LoggerFactory.getLogger(OpenAPSSMBFragment.class);

    @BindView(R.id.openapsma_run)
    Button run;
    @BindView(R.id.openapsma_lastrun)
    TextView lastRunView;
    @BindView(R.id.openapsma_constraints)
    TextView constraintsView;
    @BindView(R.id.openapsma_glucosestatus)
    TextView glucoseStatusView;
    @BindView(R.id.openapsma_currenttemp)
    TextView currentTempView;
    @BindView(R.id.openapsma_iobdata)
    TextView iobDataView;
    @BindView(R.id.openapsma_profile)
    TextView profileView;
    @BindView(R.id.openapsma_mealdata)
    TextView mealDataView;
    @BindView(R.id.openapsma_autosensdata)
    TextView autosensDataView;
    @BindView(R.id.openapsma_result)
    TextView resultView;
    @BindView(R.id.openapsma_scriptdebugdata)
    TextView scriptdebugView;
    @BindView(R.id.openapsma_request)
    TextView requestView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.openapsama_fragment, container, false);

        unbinder = ButterKnife.bind(this, view);
        return view;
    }

    @OnClick(R.id.openapsma_run)
    public void onRunClick() {
        OpenAPSSMBPlugin.getPlugin().invoke("OpenAPSSMB button", false);
        FabricPrivacy.getInstance().logCustom(new CustomEvent("OpenAPS_SMB_Run"));
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
                    OpenAPSSMBPlugin plugin = OpenAPSSMBPlugin.getPlugin();
                    DetermineBasalResultSMB lastAPSResult = plugin.lastAPSResult;
                    if (lastAPSResult != null) {
                        resultView.setText(JSONFormatter.format(lastAPSResult.json));
                        requestView.setText(lastAPSResult.toSpanned());
                    }
                    DetermineBasalAdapterSMBJS determineBasalAdapterSMBJS = plugin.lastDetermineBasalAdapterSMBJS;
                    if (determineBasalAdapterSMBJS != null) {
                        glucoseStatusView.setText(JSONFormatter.format(determineBasalAdapterSMBJS.getGlucoseStatusParam()).toString().trim());
                        currentTempView.setText(JSONFormatter.format(determineBasalAdapterSMBJS.getCurrentTempParam()).toString().trim());
                        try {
                            JSONArray iobArray = new JSONArray(determineBasalAdapterSMBJS.getIobDataParam());
                            iobDataView.setText((String.format(MainApp.gs(R.string.array_of_elements), iobArray.length()) + "\n" + JSONFormatter.format(iobArray.getString(0))).trim());
                        } catch (JSONException e) {
                            log.error("Unhandled exception", e);
                            iobDataView.setText("JSONException see log for details");
                        }
                        profileView.setText(JSONFormatter.format(determineBasalAdapterSMBJS.getProfileParam()).toString().trim());
                        mealDataView.setText(JSONFormatter.format(determineBasalAdapterSMBJS.getMealDataParam()).toString().trim());
                        scriptdebugView.setText(determineBasalAdapterSMBJS.getScriptDebug().trim());
                        if (lastAPSResult != null && lastAPSResult.inputConstraints != null)
                            constraintsView.setText(lastAPSResult.inputConstraints.getReasons().trim());
                    }
                    if (plugin.lastAPSRun != null) {
                        lastRunView.setText(plugin.lastAPSRun.toLocaleString().trim());
                    }
                    if (plugin.lastAutosensResult != null) {
                        autosensDataView.setText(JSONFormatter.format(plugin.lastAutosensResult.json()).toString().trim());
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
