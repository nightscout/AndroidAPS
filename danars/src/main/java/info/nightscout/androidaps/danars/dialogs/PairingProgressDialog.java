package info.nightscout.androidaps.danars.dialogs;


import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.fragment.app.FragmentActivity;

import javax.inject.Inject;

import dagger.android.support.DaggerDialogFragment;
import info.nightscout.androidaps.danars.R;
import info.nightscout.androidaps.danars.databinding.DanarsPairingProgressDialogBinding;
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

    private final CompositeDisposable disposable = new CompositeDisposable();

    private PairingHelperActivity helperActivity;

    private static boolean pairingEnded = false;

    private static Handler handler;
    private static HandlerThread handlerThread;

    private static Runnable runnable;

    private DanarsPairingProgressDialogBinding binding;

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
                            if (binding != null) {
                                binding.danarsPairingprogressProgressbar.setProgress(100);
                                binding.danarsPairingprogressStatus.setText(R.string.danars_pairingok);
                            }
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
                if (binding != null) binding.danarsPairingprogressProgressbar.setProgress(i * 5);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
            }
            FragmentActivity activity = getActivity();
            if (activity != null) {
                activity.runOnUiThread(() -> {
                    if (binding != null) {
                        binding.danarsPairingprogressProgressbar.setProgress(100);
                        binding.danarsPairingprogressStatus.setText(R.string.danars_pairingtimedout);
                        binding.ok.setVisibility(View.VISIBLE);
                    }
                });
            }
        };
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = DanarsPairingProgressDialogBinding.inflate(inflater, container, false);

        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        setCancelable(false);
        getDialog().setCanceledOnTouchOutside(false);

        setViews();

        return binding.getRoot();
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void setViews() {
        if (binding != null) {
            binding.danarsPairingprogressProgressbar.setMax(100);
            binding.danarsPairingprogressProgressbar.setProgress(0);
            binding.danarsPairingprogressStatus.setText(resourceHelper.gs(R.string.danars_waitingforpairing));
            binding.ok.setVisibility(View.GONE);
            binding.ok.setOnClickListener(v -> dismiss());
        }
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
