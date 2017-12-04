package info.nightscout.androidaps.plugins.Overview.Dialogs;


import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventPumpStatusChanged;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Overview.events.EventDismissBolusprogressIfRunning;
import info.nightscout.androidaps.plugins.Overview.events.EventOverviewBolusProgress;

public class BolusProgressDialog extends DialogFragment implements View.OnClickListener {
    private static Logger log = LoggerFactory.getLogger(BolusProgressDialog.class);
    Button stopButton;
    TextView statusView;
    TextView stopPressedView;
    ProgressBar progressBar;
    BolusProgressHelperActivity helperActivity;

    static double amount;
    public static boolean bolusEnded = false;
    public static boolean running = true;
    public static boolean stopPressed = false;

    public BolusProgressDialog() {
        super();
    }

    public void setInsulin(double amount) {
        this.amount = amount;
        bolusEnded = false;
    }

    public void setHelperActivity(BolusProgressHelperActivity activity) {
        this.helperActivity = activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        getDialog().setTitle(String.format(MainApp.sResources.getString(R.string.overview_bolusprogress_goingtodeliver), amount));
        View view = inflater.inflate(R.layout.overview_bolusprogress_dialog, container, false);
        stopButton = (Button) view.findViewById(R.id.overview_bolusprogress_stop);
        statusView = (TextView) view.findViewById(R.id.overview_bolusprogress_status);
        stopPressedView = (TextView) view.findViewById(R.id.overview_bolusprogress_stoppressed);
        progressBar = (ProgressBar) view.findViewById(R.id.overview_bolusprogress_progressbar);
        stopButton.setOnClickListener(this);
        progressBar.setMax(100);
        statusView.setText(MainApp.sResources.getString(R.string.waitingforpump));
        setCancelable(false);
        stopPressed = false;
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getDialog() != null)
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        MainApp.bus().register(this);
        running = true;
        if (bolusEnded) dismiss();
    }

    @Override
    public void dismiss() {
        super.dismiss();
        if (helperActivity != null) {
            helperActivity.finish();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        MainApp.bus().unregister(this);
        running = false;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.overview_bolusprogress_stop:
                log.debug("Stop bolus delivery button pressed");
                stopPressed = true;
                stopPressedView.setVisibility(View.VISIBLE);
                stopButton.setVisibility(View.INVISIBLE);
                ConfigBuilderPlugin.getActivePump().stopBolusDelivering();
                break;
        }
    }

    @Subscribe
    public void onStatusEvent(final EventOverviewBolusProgress ev) {
        Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    log.debug("Status: " + ev.status + " Percent: " + ev.percent);
                    statusView.setText(ev.status);
                    progressBar.setProgress(ev.percent);
                    if (ev.percent == 100) {
                        stopButton.setVisibility(View.INVISIBLE);
                        scheduleDismiss();
                    }
                }
            });
        }
    }

    @Subscribe
    public void onStatusEvent(final EventDismissBolusprogressIfRunning ev) {
        if (BolusProgressDialog.running) {
            dismiss();
        }
    }

    @Subscribe
    public void onStatusEvent(final EventPumpStatusChanged c) {

        Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText(c.textStatus());
                        }
                    }
            );
        }

    }

    private void scheduleDismiss() {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                SystemClock.sleep(5000);
                BolusProgressDialog.bolusEnded = true;
                Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        dismiss();
                                    } catch (Exception e) {
                                        log.error("Unhandled exception", e);
                                    }
                                }
                            });
                }
            }
        });
        t.start();
    }
}
