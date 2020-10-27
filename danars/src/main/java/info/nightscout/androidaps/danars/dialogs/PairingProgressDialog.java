package info.nightscout.androidaps.danars.dialogs;


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

import androidx.fragment.app.FragmentActivity;

import javax.inject.Inject;

import dagger.android.support.DaggerDialogFragment;
import info.nightscout.androidaps.danars.R;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.danars.activities.PairingHelperActivity;
import info.nightscout.androidaps.danars.events.EventDanaRSPairingSuccess;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;


public class PairingProgressDialog extends DaggerDialogFragment {

    @Inject ResourceHelper resourceHelper;
    @Inject RxBusWrapper rxBus;
    @Inject FabricPrivacy fabricPrivacy;

    private CompositeDisposable disposable = new CompositeDisposable();

    private TextView statusView;
    private ProgressBar progressBar;
    private Button button;
    private PairingHelperActivity helperActivity;

    private static boolean pairingEnded = false;

    private static Handler handler;
    private static HandlerThread handlerThread;

    private static Runnable runnable;

    public PairingProgressDialog() {
        super();
        // Required empty public constructor
        if (handlerThread == null) {
            handlerThread = new HandlerThread(PairingProgressDialog.class.getSimpleName());
            handlerThread.start();
            handler = new Handler(handlerThread.getLooper());
        }

        runnable = () -> {
            for (int i = 0; i < 20; i++) {
                if (pairingEnded) {
                    FragmentActivity activity = getActivity();
                    if (activity != null) {
                        activity.runOnUiThread(() -> {
                            progressBar.setProgress(100);
                            statusView.setText(R.string.danars_pairingok);
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
            FragmentActivity activity = getActivity();
            if (activity != null) {
                activity.runOnUiThread(() -> {
                    progressBar.setProgress(100);
                    statusView.setText(R.string.danars_pairingtimedout);
                    button.setVisibility(View.VISIBLE);
                });
            }
        };
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

        setViews();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        disposable.add(rxBus
                .toObservable(EventDanaRSPairingSuccess.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> pairingEnded = true, fabricPrivacy::logException)
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

    private void setViews() {
        progressBar.setMax(100);
        progressBar.setProgress(0);
        statusView.setText(resourceHelper.gs(R.string.danars_waitingforpairing));
        button.setVisibility(View.GONE);
        button.setOnClickListener(v -> dismiss());
        handler.post(runnable);
    }

    public void resetToNewPairing() {
        handler.removeCallbacks(runnable);
        setViews();
    }

    public PairingProgressDialog setHelperActivity(PairingHelperActivity activity) {
        this.helperActivity = activity;
        return this;
    }
}
