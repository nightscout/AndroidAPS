package info.nightscout.androidaps.plugins.Overview.Dialogs;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;

import com.crashlytics.android.answers.CustomEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.Objects;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.FabricPrivacy;
import info.nightscout.utils.NumberPicker;
import info.nightscout.utils.SafeParse;
import info.nightscout.utils.ToastUtils;

public class NewTreatmentDialog extends DialogFragment implements OnClickListener {
    private static Logger log = LoggerFactory.getLogger(NewTreatmentDialog.class);

    private NumberPicker editCarbs;
    private NumberPicker editInsulin;

    private Integer maxCarbs;
    private Double maxInsulin;

    //one shot guards
    private boolean accepted;
    private boolean okClicked;

    private CheckBox recordOnlyCheckbox;

    public NewTreatmentDialog() {
    }

    final private TextWatcher textWatcher = new TextWatcher() {
        @Override
        public void afterTextChanged(Editable s) {
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            validateInputs();
        }
    };

    private void validateInputs() {
        Integer carbs = SafeParse.stringToInt(editCarbs.getText());
        if (carbs > maxCarbs) {
            editCarbs.setValue(0d);
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.gs(R.string.carbsconstraintapplied));
        }
        Double insulin = SafeParse.stringToDouble(editInsulin.getText());
        if (insulin > maxInsulin) {
            editInsulin.setValue(0d);
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.gs(R.string.bolusconstraintapplied));
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.overview_newtreatment_dialog, container, false);

        view.findViewById(R.id.ok).setOnClickListener(this);
        view.findViewById(R.id.cancel).setOnClickListener(this);

        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        maxCarbs = MainApp.getConstraintChecker().getMaxCarbsAllowed().value();
        maxInsulin = MainApp.getConstraintChecker().getMaxBolusAllowed().value();

        editCarbs = (NumberPicker) view.findViewById(R.id.treatments_newtreatment_carbsamount);
        editInsulin = (NumberPicker) view.findViewById(R.id.treatments_newtreatment_insulinamount);

        editCarbs.setParams(0d, 0d, (double) maxCarbs, 1d, new DecimalFormat("0"), false, textWatcher);
        editInsulin.setParams(0d, 0d, maxInsulin, ConfigBuilderPlugin.getActivePump().getPumpDescription().bolusStep, DecimalFormatter.pumpSupportedBolusFormat(), false, textWatcher);

        recordOnlyCheckbox = (CheckBox) view.findViewById(R.id.newtreatment_record_only);

        setCancelable(true);
        getDialog().setCanceledOnTouchOutside(false);
        return view;
    }

    @Override
    public synchronized void onClick(View view) {
        switch (view.getId()) {
            case R.id.ok:
                if (okClicked) {
                    log.debug("guarding: ok already clicked");
                    dismiss();
                    return;
                }
                okClicked = true;

                try {
                    Double insulin = SafeParse.stringToDouble(editInsulin.getText());
                    final Integer carbs = SafeParse.stringToInt(editCarbs.getText());

                    String confirmMessage = MainApp.gs(R.string.entertreatmentquestion) + "<br/>";

                    Double insulinAfterConstraints = MainApp.getConstraintChecker().applyBolusConstraints(new Constraint<>(insulin)).value();
                    Integer carbsAfterConstraints = MainApp.getConstraintChecker().applyCarbsConstraints(new Constraint<>(carbs)).value();

                    if (insulin > 0) {
                        confirmMessage += MainApp.gs(R.string.bolus) + ": " + "<font color='" + MainApp.gc(R.color.colorCarbsButton) + "'>" + insulinAfterConstraints + "U" + "</font>";
                        if (recordOnlyCheckbox.isChecked()) {
                            confirmMessage += "<br/><font color='" + MainApp.gc(R.color.low) + "'>" + MainApp.gs(R.string.bolusrecordedonly) + "</font>";
                        }
                    }
                    if (carbsAfterConstraints > 0)
                        confirmMessage += "<br/>" + MainApp.gs(R.string.carbs) + ": " + carbsAfterConstraints + "g";
                    if (insulinAfterConstraints - insulin != 0 || !Objects.equals(carbsAfterConstraints, carbs))
                        confirmMessage += "<br/>" + MainApp.gs(R.string.constraintapllied);


                    final double finalInsulinAfterConstraints = insulinAfterConstraints;
                    final int finalCarbsAfterConstraints = carbsAfterConstraints;

                    final Context context = getContext();
                    final AlertDialog.Builder builder = new AlertDialog.Builder(context);

                    builder.setTitle(MainApp.gs(R.string.confirmation));
                    builder.setMessage(Html.fromHtml(confirmMessage));
                    builder.setPositiveButton(MainApp.gs(R.string.ok), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            synchronized (builder) {
                                if (accepted) {
                                    log.debug("guarding: already accepted");
                                    return;
                                }
                                accepted = true;
                                if (finalInsulinAfterConstraints > 0 || finalCarbsAfterConstraints > 0) {
                                    DetailedBolusInfo detailedBolusInfo = new DetailedBolusInfo();
                                    if (finalInsulinAfterConstraints == 0)
                                        detailedBolusInfo.eventType = CareportalEvent.CARBCORRECTION;
                                    if (finalCarbsAfterConstraints == 0)
                                        detailedBolusInfo.eventType = CareportalEvent.CORRECTIONBOLUS;
                                    detailedBolusInfo.insulin = finalInsulinAfterConstraints;
                                    detailedBolusInfo.carbs = finalCarbsAfterConstraints;
                                    detailedBolusInfo.context = context;
                                    detailedBolusInfo.source = Source.USER;
                                    if (!(recordOnlyCheckbox.isChecked() && (detailedBolusInfo.insulin > 0 || ConfigBuilderPlugin.getActivePump().getPumpDescription().storesCarbInfo))) {
                                        ConfigBuilderPlugin.getCommandQueue().bolus(detailedBolusInfo, new Callback() {
                                            @Override
                                            public void run() {
                                                if (!result.success) {
                                                    Intent i = new Intent(MainApp.instance(), ErrorHelperActivity.class);
                                                    i.putExtra("soundid", R.raw.boluserror);
                                                    i.putExtra("status", result.comment);
                                                    i.putExtra("title", MainApp.gs(R.string.treatmentdeliveryerror));
                                                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                                    MainApp.instance().startActivity(i);
                                                }
                                            }
                                        });
                                    } else {
                                        TreatmentsPlugin.getPlugin().addToHistoryTreatment(detailedBolusInfo);
                                    }
                                    FabricPrivacy.getInstance().logCustom(new CustomEvent("Bolus"));
                                }
                            }
                        }
                    });
                    builder.setNegativeButton(MainApp.gs(R.string.cancel), null);
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