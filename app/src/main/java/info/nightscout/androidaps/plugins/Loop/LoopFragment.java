package info.nightscout.androidaps.plugins.Loop;


import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.crashlytics.android.answers.CustomEvent;
import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.Common.SubscriberFragment;
import info.nightscout.androidaps.plugins.Loop.events.EventLoopSetLastRunGui;
import info.nightscout.androidaps.plugins.Loop.events.EventLoopUpdateGui;
import info.nightscout.utils.FabricPrivacy;

public class LoopFragment extends SubscriberFragment {
    private static Logger log = LoggerFactory.getLogger(LoopFragment.class);

    @BindView(R.id.loop_run)
    Button runNowButton;
    @BindView(R.id.loop_lastrun)
    TextView lastRunView;
    @BindView(R.id.loop_lastenact)
    TextView lastEnactView;
    @BindView(R.id.loop_source)
    TextView sourceView;
    @BindView(R.id.loop_request)
    TextView requestView;
    @BindView(R.id.loop_constraintsprocessed)
    TextView constraintsProcessedView;
    @BindView(R.id.loop_tbrsetbypump)
    TextView tbrSetByPumpView;
    @BindView(R.id.loop_smbsetbypump)
    TextView smbSetByPumpView;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.loop_fragment, container, false);
            unbinder = ButterKnife.bind(this, view);
            return view;
        } catch (Exception e) {
            FabricPrivacy.logException(e);
        }
        return null;
    }

    @OnClick(R.id.loop_run)
    void onRunClick() {
        lastRunView.setText(MainApp.sResources.getString(R.string.executing));
        new Thread(() -> LoopPlugin.getPlugin().invoke("Loop button", true)).start();
        FabricPrivacy.getInstance().logCustom(new CustomEvent("Loop_Run"));
    }

    @Subscribe
    public void onStatusEvent(final EventLoopUpdateGui ev) {
        updateGUI();
    }

    @Subscribe
    public void onStatusEvent(final EventLoopSetLastRunGui ev) {
        clearGUI();
        final Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(() -> lastRunView.setText(ev.text));
    }


    @Override
    protected void updateGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(() -> {
                if (LoopPlugin.lastRun != null) {
                    requestView.setText(LoopPlugin.lastRun.request != null ? LoopPlugin.lastRun.request.toSpanned() : "");
                    constraintsProcessedView.setText(LoopPlugin.lastRun.constraintsProcessed != null ? LoopPlugin.lastRun.constraintsProcessed.toSpanned() : "");
                    sourceView.setText(LoopPlugin.lastRun.source != null ? LoopPlugin.lastRun.source : "");
                    lastRunView.setText(LoopPlugin.lastRun.lastAPSRun != null && LoopPlugin.lastRun.lastAPSRun.getTime() != 0 ? LoopPlugin.lastRun.lastAPSRun.toLocaleString() : "");
                    lastEnactView.setText(LoopPlugin.lastRun.lastEnact != null && LoopPlugin.lastRun.lastEnact.getTime() != 0 ? LoopPlugin.lastRun.lastEnact.toLocaleString() : "");
                    tbrSetByPumpView.setText(LoopPlugin.lastRun.tbrSetByPump != null ? LoopPlugin.lastRun.tbrSetByPump.toSpanned() : "");
                    smbSetByPumpView.setText(LoopPlugin.lastRun.smbSetByPump != null ? LoopPlugin.lastRun.smbSetByPump.toSpanned() : "");
                }
            });
    }

    void clearGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(() -> {
                requestView.setText("");
                constraintsProcessedView.setText("");
                sourceView.setText("");
                lastRunView.setText("");
                lastEnactView.setText("");
                tbrSetByPumpView.setText("");
                smbSetByPumpView.setText("");
            });
    }
}
