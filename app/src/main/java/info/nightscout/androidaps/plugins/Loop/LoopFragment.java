package info.nightscout.androidaps.plugins.Loop;


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
import info.nightscout.androidaps.plugins.Loop.events.EventLoopSetLastRunGui;
import info.nightscout.androidaps.plugins.Loop.events.EventLoopUpdateGui;

public class LoopFragment extends Fragment implements View.OnClickListener, FragmentBase {
    private static Logger log = LoggerFactory.getLogger(LoopFragment.class);

    private static LoopPlugin loopPlugin = new LoopPlugin();

    public static LoopPlugin getPlugin() {
        return loopPlugin;
    }

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

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.loop_run:
                loopPlugin.invoke(true);
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


    void updateGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (loopPlugin.lastRun != null) {
                        requestView.setText(loopPlugin.lastRun.request != null ? loopPlugin.lastRun.request.toSpanned() : "");
                        constraintsProcessedView.setText(loopPlugin.lastRun.constraintsProcessed != null ? loopPlugin.lastRun.constraintsProcessed.toSpanned() : "");
                        setByPumpView.setText(loopPlugin.lastRun.setByPump != null ? loopPlugin.lastRun.setByPump.toSpanned() : "");
                        sourceView.setText(loopPlugin.lastRun.source != null ? loopPlugin.lastRun.source : "");
                        lastRunView.setText(loopPlugin.lastRun.lastAPSRun != null && loopPlugin.lastRun.lastAPSRun.getTime() != 0 ? loopPlugin.lastRun.lastAPSRun.toLocaleString() : "");
                        lastEnactView.setText(loopPlugin.lastRun.lastEnact != null && loopPlugin.lastRun.lastEnact.getTime() != 0 ? loopPlugin.lastRun.lastEnact.toLocaleString() : "");
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
