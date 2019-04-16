package info.nightscout.androidaps.plugins.aps.loop;


import android.app.Activity;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.plugins.aps.loop.events.EventLoopSetLastRunGui;
import info.nightscout.androidaps.plugins.aps.loop.events.EventLoopUpdateGui;
import info.nightscout.androidaps.plugins.common.SubscriberFragment;
import info.nightscout.androidaps.utils.FabricPrivacy;

public class LoopFragment extends SubscriberFragment {
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
    @BindView(R.id.loop_constraints)
    TextView constraintsView;
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
        lastRunView.setText(MainApp.gs(R.string.executing));
        new Thread(() -> LoopPlugin.getPlugin().invoke("Loop button", true)).start();
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
            activity.runOnUiThread(() -> {
                synchronized (LoopFragment.this) {
                    if (lastRunView != null) lastRunView.setText(ev.text);
                }
            });
    }


    @Override
    protected void updateGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(() -> {
                synchronized (LoopFragment.this) {
                    if (!isBound()) return;
                    LoopPlugin.LastRun lastRun = LoopPlugin.lastRun;
                    if (lastRun != null) {
                        requestView.setText(lastRun.request != null ? lastRun.request.toSpanned() : "");
                        constraintsProcessedView.setText(lastRun.constraintsProcessed != null ? lastRun.constraintsProcessed.toSpanned() : "");
                        sourceView.setText(lastRun.source != null ? lastRun.source : "");
                        lastRunView.setText(lastRun.lastAPSRun != null && lastRun.lastAPSRun.getTime() != 0 ? lastRun.lastAPSRun.toLocaleString() : "");
                        lastEnactView.setText(lastRun.lastEnact != null && lastRun.lastEnact.getTime() != 0 ? lastRun.lastEnact.toLocaleString() : "");
                        tbrSetByPumpView.setText(lastRun.tbrSetByPump != null ? Html.fromHtml(lastRun.tbrSetByPump.toHtml()) : "");
                        smbSetByPumpView.setText(lastRun.smbSetByPump != null ? Html.fromHtml(lastRun.smbSetByPump.toHtml()) : "");

                        String constraints = "";
                        if (lastRun.constraintsProcessed != null) {
                            Constraint<Double> allConstraints = new Constraint<>(0d);
                            if (lastRun.constraintsProcessed.rateConstraint != null)
                                allConstraints.copyReasons(lastRun.constraintsProcessed.rateConstraint);
                            if (lastRun.constraintsProcessed.smbConstraint != null)
                                allConstraints.copyReasons(lastRun.constraintsProcessed.smbConstraint);
                            constraints = allConstraints.getMostLimitedReasons();
                        }
                        constraintsView.setText(constraints);
                    }
                }
            });
    }

    void clearGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(() -> {
                synchronized (LoopFragment.this) {
                    if (isBound()) {
                        requestView.setText("");
                        constraintsProcessedView.setText("");
                        sourceView.setText("");
                        lastRunView.setText("");
                        lastEnactView.setText("");
                        tbrSetByPumpView.setText("");
                        smbSetByPumpView.setText("");
                    }
                }
            });
    }

    boolean isBound() {
        return requestView != null
                && constraintsProcessedView != null
                && sourceView != null
                && lastRunView != null
                && lastEnactView != null
                && tbrSetByPumpView != null
                && smbSetByPumpView != null
                && constraintsView != null
                && runNowButton != null;
    }
}
