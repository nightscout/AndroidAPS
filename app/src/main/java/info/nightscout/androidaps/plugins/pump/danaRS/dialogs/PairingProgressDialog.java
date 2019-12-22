package info.nightscout.androidaps.plugins.pump.danaRS.dialogs;


import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.fragment.app.DialogFragment;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.pump.danaRS.activities.PairingHelperActivity;
import info.nightscout.androidaps.plugins.pump.danaRS.events.EventDanaRSPairingSuccess;
import info.nightscout.androidaps.utils.FabricPrivacy;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;


public class PairingProgressDialog extends DialogFragment {
    private CompositeDisposable disposable = new CompositeDisposable();

    private TextView statusView;
    private ProgressBar progressBar;
    private Button button;
    private PairingHelperActivity helperActivity;

    private static boolean pairingEnded = false;

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
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.danars_pairingprogressdialog, container, false);

        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        setCancelable(false);
        getDialog().setCanceledOnTouchOutside(false);

        statusView = view.findViewById(R.id.danars_pairingprogress_status);
        progressBar = view.findViewById(R.id.danars_pairingprogress_progressbar);
        button = view.findViewById(R.id.ok);

        progressBar.setMax(100);
        progressBar.setProgress(0);
        statusView.setText(MainApp.gs(R.string.waitingforpairing));
        button.setVisibility(View.GONE);
        button.setOnClickListener(v -> dismiss());

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
        if (pairingEnded) dismiss();
        getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
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
    }

    public PairingProgressDialog setHelperActivity(PairingHelperActivity activity) {
        this.helperActivity = activity;
        return this;
    }
}
