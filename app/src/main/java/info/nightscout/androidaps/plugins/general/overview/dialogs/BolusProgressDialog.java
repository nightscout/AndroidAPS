package info.nightscout.androidaps.plugins.general.overview.dialogs;


import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventPumpStatusChanged;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissBolusprogressIfRunning;
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress;

public class BolusProgressDialog extends DialogFragment implements View.OnClickListener {
    private static Logger log = LoggerFactory.getLogger(L.UI);
    Button stopButton;
    TextView statusView;
    TextView stopPressedView;
    ProgressBar progressBar;
    BolusProgressHelperActivity helperActivity;

    static double amount;
    public static boolean bolusEnded = false;
    public static boolean running = true;
    public static boolean stopPressed = false;

    private String state;
    private final static String DEFAULT_STATE = MainApp.gs(R.string.waitingforpump);

    public BolusProgressDialog() {
        super();
    }

    public void setInsulin(double amount) {
        BolusProgressDialog.amount = amount;
        bolusEnded = false;
    }

    public void setHelperActivity(BolusProgressHelperActivity activity) {
        this.helperActivity = activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        getDialog().setTitle(String.format(MainApp.gs(R.string.overview_bolusprogress_goingtodeliver), amount));
        View view = inflater.inflate(R.layout.overview_bolusprogress_dialog, container, false);
        stopButton = view.findViewById(R.id.overview_bolusprogress_stop);
        statusView = view.findViewById(R.id.overview_bolusprogress_status);
        stopPressedView = view.findViewById(R.id.overview_bolusprogress_stoppressed);
        progressBar = view.findViewById(R.id.overview_bolusprogress_progressbar);
        stopButton.setOnClickListener(this);
        progressBar.setMax(100);
        state = savedInstanceState != null ? savedInstanceState.getString("state", DEFAULT_STATE) : DEFAULT_STATE;
        statusView.setText(state);
        setCancelable(false);
        stopPressed = false;
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (L.isEnabled(L.UI))
            log.debug("onResume");
        if (!ConfigBuilderPlugin.getPlugin().getCommandQueue().bolusInQueue()) {
            bolusEnded = true;
        }
        if (bolusEnded) {
            dismiss();
        } else {
            if (getDialog() != null)
                getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            running = true;
            if (L.isEnabled(L.UI))
                log.debug("onResume running");
        }
        try {
            MainApp.bus().register(this);
        } catch (IllegalArgumentException e) {
            log.error("Already registered");
        }
    }

    @Override
    public void dismiss() {
        if (L.isEnabled(L.UI))
            log.debug("dismiss");
        try {
            super.dismiss();
        } catch (IllegalStateException e) {
            // dialog not running yet. onResume will try again. Set bolusEnded to make extra
            // sure onResume will catch this
            bolusEnded = true;
            log.error("Unhandled exception", e);
        }
        if (helperActivity != null) {
            helperActivity.finish();
        }
    }

    @Override
    public void onPause() {
        if (L.isEnabled(L.UI))
            log.debug("onPause");
        running = false;
        super.onPause();
        try {
            MainApp.bus().unregister(this);
        } catch (IllegalArgumentException e) {
            log.error("Already unregistered");
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString("state", state);
        log.debug("storing state: " + state);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.overview_bolusprogress_stop:
                if (L.isEnabled(L.UI))
                    log.debug("Stop bolus delivery button pressed");
                stopPressed = true;
                stopPressedView.setVisibility(View.VISIBLE);
                stopButton.setVisibility(View.INVISIBLE);
                ConfigBuilderPlugin.getPlugin().getCommandQueue().cancelAllBoluses();
                break;
        }
    }

    @Subscribe
    public void onStatusEvent(final EventOverviewBolusProgress ev) {
        Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(() -> {
                if (L.isEnabled(L.UI))
                    log.debug("Status: " + ev.status + " Percent: " + ev.percent);
                statusView.setText(ev.status);
                progressBar.setProgress(ev.percent);
                if (ev.percent == 100) {
                    stopButton.setVisibility(View.INVISIBLE);
                    scheduleDismiss();
                }
            });
        }
        state = ev.status;
    }

    @Subscribe
    public void onStatusEvent(final EventDismissBolusprogressIfRunning ev) {
        if (L.isEnabled(L.UI))
            log.debug("EventDismissBolusprogressIfRunning");
        if (BolusProgressDialog.running) {
            dismiss();
        }
    }

    @Subscribe
    public void onStatusEvent(final EventPumpStatusChanged c) {
        if (L.isEnabled(L.UI))
            log.debug("EventPumpStatusChanged");
        Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(() -> statusView.setText(c.textStatus()));
        }
    }

    private void scheduleDismiss() {
        if (L.isEnabled(L.UI))
            log.debug("scheduleDismiss");
        Thread t = new Thread(() -> {
            SystemClock.sleep(5000);
            BolusProgressDialog.bolusEnded = true;
            Activity activity = getActivity();
            if (activity != null) {
                activity.runOnUiThread(() -> {
                    try {
                        if (running) {
                            if (L.isEnabled(L.UI))
                                log.debug("executing");
                            dismiss();
                        }
                    } catch (Exception e) {
                        log.error("Unhandled exception", e);
                    }
                });
            }
        });
        t.start();
    }
}
