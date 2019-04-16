package info.nightscout.androidaps.plugins.aps.openAPSMA;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.aps.openAPSMA.events.EventOpenAPSUpdateGui;
import info.nightscout.androidaps.plugins.aps.openAPSMA.events.EventOpenAPSUpdateResultGui;
import info.nightscout.androidaps.plugins.common.SubscriberFragment;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.JSONFormatter;

public class OpenAPSMAFragment extends SubscriberFragment implements View.OnClickListener {
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
        try {
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
        } catch (Exception e) {
            FabricPrivacy.logException(e);
        }

        return null;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.openapsma_run:
                OpenAPSMAPlugin.getPlugin().invoke("OpenAPSMA button", false);
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
            activity.runOnUiThread(() -> {
                DetermineBasalResultMA lastAPSResult = OpenAPSMAPlugin.getPlugin().lastAPSResult;
                if (lastAPSResult != null) {
                    resultView.setText(JSONFormatter.format(lastAPSResult.json));
                    requestView.setText(lastAPSResult.toSpanned());
                }
                DetermineBasalAdapterMAJS determineBasalAdapterMAJS = OpenAPSMAPlugin.getPlugin().lastDetermineBasalAdapterMAJS;
                if (determineBasalAdapterMAJS != null) {
                    glucoseStatusView.setText(JSONFormatter.format(determineBasalAdapterMAJS.getGlucoseStatusParam()));
                    currentTempView.setText(JSONFormatter.format(determineBasalAdapterMAJS.getCurrentTempParam()));
                    iobDataView.setText(JSONFormatter.format(determineBasalAdapterMAJS.getIobDataParam()));
                    profileView.setText(JSONFormatter.format(determineBasalAdapterMAJS.getProfileParam()));
                    mealDataView.setText(JSONFormatter.format(determineBasalAdapterMAJS.getMealDataParam()));
                }
                if (OpenAPSMAPlugin.getPlugin().lastAPSRun != 0) {
                    lastRunView.setText(DateUtil.dateAndTimeFullString(OpenAPSMAPlugin.getPlugin().lastAPSRun));
                }
            });
    }

    private void updateResultGUI(final String text) {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(() -> {
                resultView.setText(text);
                glucoseStatusView.setText("");
                currentTempView.setText("");
                iobDataView.setText("");
                profileView.setText("");
                mealDataView.setText("");
                requestView.setText("");
                lastRunView.setText("");
            });
    }
}
