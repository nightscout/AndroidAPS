package info.nightscout.androidaps.plugins.pump.danaRS.activities;


import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.fragment.app.DialogFragment;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.pump.danaRS.events.EventDanaRSPairingSuccess;
import info.nightscout.androidaps.utils.FabricPrivacy;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;


public class PairingProgressDialog extends DialogFragment implements View.OnClickListener {
    private CompositeDisposable disposable = new CompositeDisposable();

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
        statusView = (TextView) view.findViewById(R.id.danars_pairingprogress_status);
        progressBar = (ProgressBar) view.findViewById(R.id.danars_pairingprogress_progressbar);
        button = (Button) view.findViewById(R.id.ok);

        progressBar.setMax(100);
        progressBar.setProgress(0);
        statusView.setText(MainApp.gs(R.string.waitingforpairing));
        button.setVisibility(View.GONE);
        button.setOnClickListener(this);
        setCancelable(false);

        sHandler.post(() -> {
            for (int i = 0; i < 20; i++) {
                if (pairingEnded) {
                    Activity activity = getActivity();
                    if (activity != null) {
                        activity.runOnUiThread(() -> {
                            progressBar.setProgress(100);
                            statusView.setText(R.string.pairingok);
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException ignored) {
                            }
                            dismiss();
                        });
                    } else
                        dismiss();
                    return;
                }
                progressBar.setProgress(i * 5);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
            }
            Activity activity = getActivity();
            if (activity != null) {
                activity.runOnUiThread(() -> {
                    progressBar.setProgress(100);
                    statusView.setText(R.string.pairingtimedout);
                    button.setVisibility(View.VISIBLE);
                });
            }
        });
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        disposable.add(RxBus.INSTANCE
                .toObservable(EventDanaRSPairingSuccess.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> pairingEnded = true, FabricPrivacy::logException)
        );
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
        disposable.clear();
        running = false;
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
