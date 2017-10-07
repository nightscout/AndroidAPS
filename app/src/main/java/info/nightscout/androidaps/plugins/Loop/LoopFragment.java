package info.nightscout.androidaps.plugins.Loop;


import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.Common.SubscriberFragment;
import info.nightscout.androidaps.plugins.Loop.events.EventLoopSetLastRunGui;
import info.nightscout.androidaps.plugins.Loop.events.EventLoopUpdateGui;

public class LoopFragment extends SubscriberFragment implements View.OnClickListener {
    private static Logger log = LoggerFactory.getLogger(LoopFragment.class);

    Button runNowButton;
    TextView lastRunView;
    TextView lastEnactView;
    TextView sourceView;
    TextView requestView;
    TextView constraintsProcessedView;
    TextView setByPumpView;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.loop_fragment, container, false);

            lastRunView = (TextView) view.findViewById(R.id.loop_lastrun);
            lastEnactView = (TextView) view.findViewById(R.id.loop_lastenact);
            sourceView = (TextView) view.findViewById(R.id.loop_source);
            requestView = (TextView) view.findViewById(R.id.loop_request);
            constraintsProcessedView = (TextView) view.findViewById(R.id.loop_constraintsprocessed);
            setByPumpView = (TextView) view.findViewById(R.id.loop_setbypump);
            runNowButton = (Button) view.findViewById(R.id.loop_run);
            runNowButton.setOnClickListener(this);

            updateGUI();
            return view;
        } catch (Exception e) {
            Crashlytics.logException(e);
        }

        return null;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.loop_run:
                lastRunView.setText(MainApp.sResources.getString(R.string.executing));
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        LoopPlugin.getPlugin().invoke("Loop button", true);
                    }
                });
                thread.start();
                Answers.getInstance().logCustom(new CustomEvent("Loop_Run"));
                break;
        }

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
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    lastRunView.setText(ev.text);
                }
            });
    }


    @Override
    protected void updateGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (LoopPlugin.lastRun != null) {
                        requestView.setText(LoopPlugin.lastRun.request != null ? LoopPlugin.lastRun.request.toSpanned() : "");
                        constraintsProcessedView.setText(LoopPlugin.lastRun.constraintsProcessed != null ? LoopPlugin.lastRun.constraintsProcessed.toSpanned() : "");
                        setByPumpView.setText(LoopPlugin.lastRun.setByPump != null ? LoopPlugin.lastRun.setByPump.toSpanned() : "");
                        sourceView.setText(LoopPlugin.lastRun.source != null ? LoopPlugin.lastRun.source : "");
                        lastRunView.setText(LoopPlugin.lastRun.lastAPSRun != null && LoopPlugin.lastRun.lastAPSRun.getTime() != 0 ? LoopPlugin.lastRun.lastAPSRun.toLocaleString() : "");
                        lastEnactView.setText(LoopPlugin.lastRun.lastEnact != null && LoopPlugin.lastRun.lastEnact.getTime() != 0 ? LoopPlugin.lastRun.lastEnact.toLocaleString() : "");
                    }
                }
            });
    }

    void clearGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    requestView.setText("");
                    constraintsProcessedView.setText("");
                    setByPumpView.setText("");
                    sourceView.setText("");
                    lastRunView.setText("");
                    lastEnactView.setText("");
                }
            });
    }
}
