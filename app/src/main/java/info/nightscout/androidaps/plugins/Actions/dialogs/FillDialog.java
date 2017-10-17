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
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Overview.Notification;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.NumberPicker;
import info.nightscout.utils.SP;
import info.nightscout.utils.SafeParse;

public class FillDialog extends DialogFragment implements OnClickListener {
    private static Logger log = LoggerFactory.getLogger(FillDialog.class);

    Button deliverButton;

    double amount1 = 0d;
    double amount2 = 0d;
    double amount3 = 0d;

    NumberPicker editInsulin;

    Handler mHandler;
    public static HandlerThread mHandlerThread;

    public FillDialog() {
        mHandlerThread = new HandlerThread(FillDialog.class.getSimpleName());
        mHandlerThread.start();
        this.mHandler = new Handler(mHandlerThread.getLooper());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.actions_fill_dialog, null, false);

        deliverButton = (Button) view.findViewById(R.id.treatments_newtreatment_deliverbutton);

        deliverButton.setOnClickListener(this);
        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        Double maxInsulin = MainApp.getConfigBuilder().applyBolusConstraints(Constants.bolusOnlyForCheckLimit);
        double bolusstep = MainApp.getConfigBuilder().getPumpDescription().bolusStep;
        editInsulin = (NumberPicker) view.findViewById(R.id.treatments_newtreatment_insulinamount);
        editInsulin.setParams(0d, 0d, maxInsulin, bolusstep, new DecimalFormat("0.00"), false);

        //setup preset buttons
        Button button1 = (Button) view.findViewById(R.id.fill_preset_button1);
        Button button2 = (Button) view.findViewById(R.id.fill_preset_button2);
        Button button3 = (Button) view.findViewById(R.id.fill_preset_button3);
        View divider = view.findViewById(R.id.fill_preset_divider);

        amount1 = SP.getDouble("fill_button1", 0.3);
        amount2 = SP.getDouble("fill_button2", 0d);
        amount3 = SP.getDouble("fill_button3", 0d);

        if (amount1 > 0) {
            button1.setVisibility(View.VISIBLE);
            button1.setText(DecimalFormatter.to2Decimal(amount1) + "U");
            button1.setOnClickListener(this);
        } else {
            button1.setVisibility(View.GONE);
        }
        if (amount2 > 0) {
            button2.setVisibility(View.VISIBLE);
            button2.setText(DecimalFormatter.to2Decimal(amount2) + "U");
            button2.setOnClickListener(this);
        } else {
            button2.setVisibility(View.GONE);
        }
        if (amount3 > 0) {
            button3.setVisibility(View.VISIBLE);
            button3.setText(DecimalFormatter.to2Decimal(amount3) + "U");
            button3.setOnClickListener(this);
        } else {
            button3.setVisibility(View.GONE);
        }

        if (button1.getVisibility() == View.GONE && button2.getVisibility() == View.GONE && button3.getVisibility() == View.GONE) {
            divider.setVisibility(View.GONE);
        }
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
            case R.id.treatments_newtreatment_deliverbutton:
                Double insulin = SafeParse.stringToDouble(editInsulin.getText().toString());
                confirmAndDeliver(insulin);
                break;
            case R.id.fill_preset_button1:
                confirmAndDeliver(amount1);
                break;
            case R.id.fill_preset_button2:
                confirmAndDeliver(amount2);
                break;
            case R.id.fill_preset_button3:
                confirmAndDeliver(amount3);
                break;
        }

    }

    private void confirmAndDeliver(Double insulin) {
        try {

            String confirmMessage = getString(R.string.fillwarning) + "\n";

            Double insulinAfterConstraints = MainApp.getConfigBuilder().applyBolusConstraints(insulin);
            confirmMessage += getString(R.string.bolus) + ": " + insulinAfterConstraints + "U";
            if (insulinAfterConstraints - insulin != 0)
                confirmMessage += "\n" + getString(R.string.constraintapllied);

            final Double finalInsulinAfterConstraints = insulinAfterConstraints;

            final Context context = getContext();
            AlertDialog.Builder builder = new AlertDialog.Builder(context);

            builder.setTitle(this.getContext().getString(R.string.confirmation));
            builder.setMessage(confirmMessage);
            builder.setPositiveButton(getString(R.string.primefill), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    if (finalInsulinAfterConstraints > 0) {
                        final ConfigBuilderPlugin pump = MainApp.getConfigBuilder();
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                DetailedBolusInfo detailedBolusInfo = new DetailedBolusInfo();
                                detailedBolusInfo.insulin = finalInsulinAfterConstraints;
                                detailedBolusInfo.context = context;
                                detailedBolusInfo.source = Source.USER;
                                detailedBolusInfo.isValid = false; // do not count it in IOB (for pump history)
                                PumpEnactResult result = pump.deliverTreatment(detailedBolusInfo);
                                if (!result.success) {
                                    try {
                                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                                        builder.setTitle(MainApp.sResources.getString(R.string.treatmentdeliveryerror));
                                        builder.setMessage(result.comment);
                                        builder.setPositiveButton(MainApp.sResources.getString(R.string.ok), null);
                                        builder.show();
                                    } catch (WindowManager.BadTokenException | NullPointerException e) {
                                        // window has been destroyed
                                        Notification notification = new Notification(Notification.BOLUS_DELIVERY_ERROR, MainApp.sResources.getString(R.string.treatmentdeliveryerror), Notification.URGENT);
                                        MainApp.bus().post(new EventNewNotification(notification));
                                    }
                                }
                            }
                        });
                        Answers.getInstance().logCustom(new CustomEvent("Fill"));
                    }
                }
            });
            builder.setNegativeButton(getString(R.string.cancel), null);
            builder.show();
            dismiss();
        } catch (RuntimeException e) {
            log.error("Unhandled exception", e);
        }
    }

}