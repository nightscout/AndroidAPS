package info.nightscout.androidaps.plugins.Actions.dialogs;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;

import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.Overview.Notification;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.utils.PlusMinusEditText;
import info.nightscout.utils.SafeParse;

public class NewExtendedBolusDialog extends DialogFragment implements View.OnClickListener {
    private static Logger log = LoggerFactory.getLogger(NewExtendedBolusDialog.class);

    PlusMinusEditText editInsulin;
    PlusMinusEditText editDuration;

    Handler mHandler;
    public static HandlerThread mHandlerThread;

    public NewExtendedBolusDialog() {
        mHandlerThread = new HandlerThread(NewExtendedBolusDialog.class.getSimpleName());
        mHandlerThread.start();
        this.mHandler = new Handler(mHandlerThread.getLooper());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        getDialog().setTitle(getString(R.string.overview_extendedbolus_button));

        View view = inflater.inflate(R.layout.overview_newextendedbolus_dialog, container, false);

        Double maxInsulin = MainApp.getConfigBuilder().applyBolusConstraints(Constants.bolusOnlyForCheckLimit);
        editInsulin = new PlusMinusEditText(view, R.id.overview_newextendedbolus_insulin, R.id.overview_newextendedbolus_insulin_plus, R.id.overview_newextendedbolus_insulin_minus, 0d, 0d, maxInsulin, 0.1d, new DecimalFormat("0.00"), false);

        double extendedDurationStep = MainApp.getConfigBuilder().getPumpDescription().extendedBolusDurationStep;
        double extendedMaxDuration = MainApp.getConfigBuilder().getPumpDescription().extendedBolusMaxDuration;
        editDuration = new PlusMinusEditText(view, R.id.overview_newextendedbolus_duration, R.id.overview_newextendedbolus_duration_plus, R.id.overview_newextendedbolus_duration_minus, extendedDurationStep, extendedDurationStep, extendedMaxDuration, extendedDurationStep, new DecimalFormat("0"), false);

        view.findViewById(R.id.ok).setOnClickListener(this);
        view.findViewById(R.id.cancel).setOnClickListener(this);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getDialog() != null)
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.ok:
                try {
                    Double insulin = SafeParse.stringToDouble(editInsulin.getText());
                    int durationInMinutes = SafeParse.stringToInt(editDuration.getText());

                    String confirmMessage = getString(R.string.setextendedbolusquestion);

                    Double insulinAfterConstraint = MainApp.getConfigBuilder().applyBolusConstraints(insulin);
                    confirmMessage += " " + insulinAfterConstraint + " U  ";
                    confirmMessage += getString(R.string.duration) + " " + durationInMinutes + "min ?";
                    if (insulinAfterConstraint - insulin != 0d)
                        confirmMessage += "\n" + getString(R.string.constraintapllied);
                    insulin = insulinAfterConstraint;

                    final Double finalInsulin = insulin;
                    final int finalDurationInMinutes = durationInMinutes;

                    final Context context = getContext();
                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setTitle(context.getString(R.string.confirmation));
                    builder.setMessage(confirmMessage);
                    builder.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            final PumpInterface pump = MainApp.getConfigBuilder();
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    PumpEnactResult result = pump.setExtendedBolus(finalInsulin, finalDurationInMinutes);
                                    if (!result.success) {
                                        try {
                                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                                        builder.setTitle(context.getString(R.string.treatmentdeliveryerror));
                                        builder.setMessage(result.comment);
                                        builder.setPositiveButton(context.getString(R.string.ok), null);
                                        builder.show();
                                        } catch (WindowManager.BadTokenException | NullPointerException e) {
                                            // window has been destroyed
                                            Notification notification = new Notification(Notification.BOLUS_DELIVERY_ERROR, MainApp.sResources.getString(R.string.treatmentdeliveryerror), Notification.URGENT);
                                            MainApp.bus().post(new EventNewNotification(notification));
                                        }
                                    }
                                }
                            });
                            Answers.getInstance().logCustom(new CustomEvent("ExtendedBolus"));
                        }
                    });
                    builder.setNegativeButton(getString(R.string.cancel), null);
                    builder.show();
                    dismiss();

                } catch (Exception e) {
                    log.error("Unhandled exception", e);
                }
                break;
            case R.id.cancel:
                dismiss();
                break;
        }
    }

}
