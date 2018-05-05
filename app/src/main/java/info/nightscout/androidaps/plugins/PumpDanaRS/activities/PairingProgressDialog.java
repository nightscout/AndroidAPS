package info.nightscout.androidaps.plugins.PumpDanaRS.activities;


import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.PumpDanaRS.events.EventDanaRSPairingSuccess;


public class PairingProgressDialog extends DialogFragment implements View.OnClickListener {

    TextView statusView;
    ProgressBar progressBar;
    Button button;
    PairingHelperActivity helperActivity;

    static int secondsPassed = 0;
    public static boolean pairingEnded = false;
    public static boolean running = true;

    private static Handler sHandler;
    private static HandlerThread sHandlerThread;

    public PairingProgressDialog() {
        super();
        // Required empty public constructor
        if (sHandlerThread == null) {
            sHandlerThread = new HandlerThread(PairingProgressDialog.class.getSimpleName());
            sHandlerThread.start();
            sHandler = new Handler(sHandlerThread.getLooper());
        }
        secondsPassed = 0;
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.danars_pairingprogressdialog, container, false);
        getDialog().setTitle(MainApp.gs(R.string.pairing));
        statusView = (TextView) view.findViewById(R.id.danars_paringprogress_status);
        progressBar = (ProgressBar) view.findViewById(R.id.danars_paringprogress_progressbar);
        button = (Button) view.findViewById(R.id.ok);

        progressBar.setMax(100);
        progressBar.setProgress(0);
        statusView.setText(MainApp.gs(R.string.waitingforpairing));
        button.setVisibility(View.GONE);
        button.setOnClickListener(this);
        setCancelable(false);

        sHandler.post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 20; i++) {
                    if (pairingEnded) {
                        Activity activity = getActivity();
                        if (activity != null) {
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    progressBar.setProgress(100);
                                    statusView.setText(R.string.pairingok);
                                    try {
                                        Thread.sleep(1000);
                                    } catch (InterruptedException e) {
                                    }
                                    dismiss();
                                }
                            });
                        } else
                            dismiss();
                        return;
                    }
                    progressBar.setProgress(i * 5);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                    }
                }
                Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setProgress(100);
                            statusView.setText(R.string.pairingtimedout);
                            button.setVisibility(View.VISIBLE);
                        }
                    });
                }
            }
        });
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        MainApp.bus().register(this);
        running = true;
        if (pairingEnded) dismiss();
    }

    @Override
    public void dismiss() {
        super.dismissAllowingStateLoss();
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

    @Subscribe
    public void onStatusEvent(final EventDanaRSPairingSuccess ev) {
        pairingEnded = true;
    }

    public void setHelperActivity(PairingHelperActivity activity) {
        this.helperActivity = activity;
    }

    @Override
    public void onClick(View v) {
        running = false;
        dismiss();
    }
}
