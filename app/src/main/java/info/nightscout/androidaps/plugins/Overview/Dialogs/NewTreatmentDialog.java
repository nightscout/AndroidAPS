package info.nightscout.androidaps.plugins.Overview.Dialogs;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.Objects;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.Overview.Notification;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.utils.NumberPicker;
import info.nightscout.utils.SafeParse;

public class NewTreatmentDialog extends DialogFragment implements OnClickListener {
    private static Logger log = LoggerFactory.getLogger(NewTreatmentDialog.class);

    NumberPicker editCarbs;
    NumberPicker editInsulin;

    Handler mHandler;
    public static HandlerThread mHandlerThread;

    public NewTreatmentDialog() {
        mHandlerThread = new HandlerThread(NewTreatmentDialog.class.getSimpleName());
        mHandlerThread.start();
        this.mHandler = new Handler(mHandlerThread.getLooper());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.overview_newtreatment_dialog, container, false);

        view.findViewById(R.id.ok).setOnClickListener(this);
        view.findViewById(R.id.cancel).setOnClickListener(this);

        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        Integer maxCarbs = MainApp.getConfigBuilder().applyCarbsConstraints(Constants.carbsOnlyForCheckLimit);
        Double maxInsulin = MainApp.getConfigBuilder().applyBolusConstraints(Constants.bolusOnlyForCheckLimit);

        editCarbs = (NumberPicker) view.findViewById(R.id.treatments_newtreatment_carbsamount);
        editInsulin = (NumberPicker) view.findViewById(R.id.treatments_newtreatment_insulinamount);

        editCarbs.setParams(0d, 0d, (double) maxCarbs, 1d, new DecimalFormat("0"), false);
        editInsulin.setParams(0d, 0d, maxInsulin, MainApp.getConfigBuilder().getPumpDescription().bolusStep, new DecimalFormat("0.00"), false);

        return view;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.ok:

                try {
                    Double insulin = SafeParse.stringToDouble(editInsulin.getText());
                    final Integer carbs = SafeParse.stringToInt(editCarbs.getText());

                    String confirmMessage = getString(R.string.entertreatmentquestion) + "<br/>";

                    Double insulinAfterConstraints = MainApp.getConfigBuilder().applyBolusConstraints(insulin);
                    Integer carbsAfterConstraints = MainApp.getConfigBuilder().applyCarbsConstraints(carbs);

                    confirmMessage += getString(R.string.bolus) + ": " + "<font color='" + MainApp.sResources.getColor(R.color.bolus) + "'>" + insulinAfterConstraints + "U" + "</font>";
                    confirmMessage += "<br/>" + getString(R.string.carbs) + ": " + carbsAfterConstraints + "g";
                    if (insulinAfterConstraints - insulin != 0 || !Objects.equals(carbsAfterConstraints, carbs))
                        confirmMessage += "<br/>" + getString(R.string.constraintapllied);


                    final double finalInsulinAfterConstraints = insulinAfterConstraints;
                    final int finalCarbsAfterConstraints = carbsAfterConstraints;

                    final Context context = getContext();
                    AlertDialog.Builder builder = new AlertDialog.Builder(context);

                    builder.setTitle(this.getContext().getString(R.string.confirmation));
                    builder.setMessage(Html.fromHtml(confirmMessage));
                    builder.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            if (finalInsulinAfterConstraints > 0 || finalCarbsAfterConstraints > 0) {
                                final PumpInterface pump = MainApp.getConfigBuilder();
                                mHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        DetailedBolusInfo detailedBolusInfo = new DetailedBolusInfo();
                                        if (finalInsulinAfterConstraints == 0)
                                            detailedBolusInfo.eventType = CareportalEvent.CARBCORRECTION;
                                        if (finalCarbsAfterConstraints == 0)
                                            detailedBolusInfo.eventType = CareportalEvent.CORRECTIONBOLUS;
                                        detailedBolusInfo.insulin = finalInsulinAfterConstraints;
                                        detailedBolusInfo.carbs = finalCarbsAfterConstraints;
                                        detailedBolusInfo.context = context;
                                        detailedBolusInfo.source = Source.USER;
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
                                Answers.getInstance().logCustom(new CustomEvent("Bolus"));
                            }
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