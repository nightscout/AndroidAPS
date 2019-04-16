package info.nightscout.androidaps.plugins.general.overview.dialogs;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.HandlerThread;
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
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.google.common.base.Joiner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.LinkedList;
import java.util.List;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.NumberPicker;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.SafeParse;
import info.nightscout.androidaps.utils.T;
import info.nightscout.androidaps.utils.ToastUtils;

import static info.nightscout.androidaps.utils.DateUtil.now;

public class NewInsulinDialog extends DialogFragment implements OnClickListener {
    private static Logger log = LoggerFactory.getLogger(NewInsulinDialog.class);

    public static final double PLUS1_DEFAULT = 0.5d;
    public static final double PLUS2_DEFAULT = 1d;
    public static final double PLUS3_DEFAULT = 2d;

    private CheckBox startEatingSoonTTCheckbox;
    private CheckBox recordOnlyCheckbox;

    private LinearLayout editLayout;
    private NumberPicker editTime;
    private NumberPicker editInsulin;
    private Double maxInsulin;

    private EditText notesEdit;

    //one shot guards
    private boolean accepted;
    private boolean okClicked;

    public NewInsulinDialog() {
        HandlerThread mHandlerThread = new HandlerThread(NewInsulinDialog.class.getSimpleName());
        mHandlerThread.start();
    }

    final private TextWatcher textWatcher = new TextWatcher() {
        @Override
        public void afterTextChanged(Editable s) {
            validateInputs();
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    };

    private void validateInputs() {
        int time = editTime.getValue().intValue();
        if (Math.abs(time) > 12 * 60) {
            editTime.setValue(0d);
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.gs(R.string.constraintapllied));
        }
        Double insulin = editInsulin.getValue();
        if (insulin > maxInsulin) {
            editInsulin.setValue(0d);
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.gs(R.string.bolusconstraintapplied));
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.overview_newinsulin_dialog, container, false);

        view.findViewById(R.id.ok).setOnClickListener(this);
        view.findViewById(R.id.cancel).setOnClickListener(this);

        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        startEatingSoonTTCheckbox = view.findViewById(R.id.newinsulin_start_eating_soon_tt);

        recordOnlyCheckbox = view.findViewById(R.id.newinsulin_record_only);
        recordOnlyCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> editLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE));

        editLayout = view.findViewById(R.id.newinsulin_time_layout);
        editLayout.setVisibility(View.GONE);
        editTime = view.findViewById(R.id.newinsulin_time);
        editTime.setParams(0d, -12 * 60d, 12 * 60d, 5d, new DecimalFormat("0"), false, textWatcher);

        maxInsulin = MainApp.getConstraintChecker().getMaxBolusAllowed().value();

        editInsulin = view.findViewById(R.id.newinsulin_amount);
        editInsulin.setParams(0d, 0d, maxInsulin, ConfigBuilderPlugin.getPlugin().getActivePump().getPumpDescription().bolusStep, DecimalFormatter.pumpSupportedBolusFormat(), false, textWatcher);

        Button plus1Button = view.findViewById(R.id.newinsulin_plus05);
        plus1Button.setOnClickListener(this);
        plus1Button.setText(toSignedString(SP.getDouble(MainApp.gs(R.string.key_insulin_button_increment_1), PLUS1_DEFAULT)));
        Button plus2Button = view.findViewById(R.id.newinsulin_plus10);
        plus2Button.setOnClickListener(this);
        plus2Button.setText(toSignedString(SP.getDouble(MainApp.gs(R.string.key_insulin_button_increment_2), PLUS2_DEFAULT)));
        Button plus3Button = view.findViewById(R.id.newinsulin_plus20);
        plus3Button.setOnClickListener(this);
        plus3Button.setText(toSignedString(SP.getDouble(MainApp.gs(R.string.key_insulin_button_increment_3), PLUS3_DEFAULT)));

        LinearLayout notesLayout = view.findViewById(R.id.newinsulin_notes_layout);
        notesLayout.setVisibility(SP.getBoolean(R.string.key_show_notes_entry_dialogs, false) ? View.VISIBLE : View.GONE);
        notesEdit = view.findViewById(R.id.newinsulin_notes);

        setCancelable(true);
        getDialog().setCanceledOnTouchOutside(false);
        if (savedInstanceState != null) {
//            log.debug("savedInstanceState in onCreate is:" + savedInstanceState.toString());
            editInsulin.setValue(savedInstanceState.getDouble("editInsulin"));
            editTime.setValue(savedInstanceState.getDouble("editTime"));
        }
        return view;
    }

    private String toSignedString(double value) {
        String formatted = DecimalFormatter.toPumpSupportedBolus(value);
        return value > 0 ? "+" + formatted : formatted;
    }

    @Override
    public void onSaveInstanceState(Bundle insulinDialogState) {
        insulinDialogState.putBoolean("startEatingSoonTTCheckbox", startEatingSoonTTCheckbox.isChecked());
        insulinDialogState.putBoolean("recordOnlyCheckbox", recordOnlyCheckbox.isChecked());
        insulinDialogState.putDouble("editTime", editTime.getValue());
        insulinDialogState.putDouble("editInsulin", editInsulin.getValue());
        insulinDialogState.putString("notesEdit", notesEdit.getText().toString());
        log.debug("Instance state saved:" + insulinDialogState.toString());
        super.onSaveInstanceState(insulinDialogState);
    }

    @Override
    public synchronized void onClick(View view) {
        switch (view.getId()) {
            case R.id.ok:
                submit();
                break;
            case R.id.cancel:
                dismiss();
                break;
            case R.id.newinsulin_plus05:
                editInsulin.setValue(Math.max(0, editInsulin.getValue()
                        + SP.getDouble(MainApp.gs(R.string.key_insulin_button_increment_1), PLUS1_DEFAULT)));
                validateInputs();
                break;
            case R.id.newinsulin_plus10:
                editInsulin.setValue(Math.max(0, editInsulin.getValue()
                        + SP.getDouble(MainApp.gs(R.string.key_insulin_button_increment_2), PLUS2_DEFAULT)));
                validateInputs();
                break;
            case R.id.newinsulin_plus20:
                editInsulin.setValue(Math.max(0, editInsulin.getValue()
                        + SP.getDouble(MainApp.gs(R.string.key_insulin_button_increment_3), PLUS3_DEFAULT)));
                validateInputs();
                break;
        }
    }

    private void submit() {
        if (okClicked) {
            log.debug("guarding: ok already clicked");
            dismiss();
            return;
        }
        okClicked = true;

        try {
            Profile currentProfile = ProfileFunctions.getInstance().getProfile();
            final PumpInterface pump = ConfigBuilderPlugin.getPlugin().getActivePump();
            if (currentProfile == null || pump == null)
                return;

            Double insulin = SafeParse.stringToDouble(editInsulin.getText());
            Double insulinAfterConstraints = MainApp.getConstraintChecker().applyBolusConstraints(new Constraint<>(insulin)).value();

            List<String> actions = new LinkedList<>();
            if (insulin > 0) {
                actions.add(MainApp.gs(R.string.bolus) + ": " + "<font color='" + MainApp.gc(R.color.bolus) + "'>" + DecimalFormatter.toPumpSupportedBolus(insulinAfterConstraints) + "U" + "</font>");
                if (recordOnlyCheckbox.isChecked()) {
                    actions.add("<font color='" + MainApp.gc(R.color.warning) + "'>" + MainApp.gs(R.string.bolusrecordedonly) + "</font>");
                }
            }

            if (Math.abs(insulinAfterConstraints - insulin) > pump.getPumpDescription().pumpType.determineCorrectBolusSize(insulinAfterConstraints))
                actions.add("<font color='" + MainApp.gc(R.color.warning) + "'>" + MainApp.gs(R.string.bolusconstraintapplied) + "</font>");

            int eatingSoonTTDuration = SP.getInt(R.string.key_eatingsoon_duration, Constants.defaultEatingSoonTTDuration);
            eatingSoonTTDuration = eatingSoonTTDuration > 0 ? eatingSoonTTDuration : Constants.defaultEatingSoonTTDuration;
            double eatingSoonTT = SP.getDouble(R.string.key_eatingsoon_target, currentProfile.getUnits().equals(Constants.MMOL) ? Constants.defaultEatingSoonTTmmol : Constants.defaultEatingSoonTTmgdl);
            eatingSoonTT = eatingSoonTT > 0 ? eatingSoonTT : currentProfile.getUnits().equals(Constants.MMOL) ? Constants.defaultEatingSoonTTmmol : Constants.defaultEatingSoonTTmgdl;

            if (startEatingSoonTTCheckbox.isChecked()) {
                if (currentProfile.getUnits().equals(Constants.MMOL)) {
                    actions.add(MainApp.gs(R.string.temptargetshort) + ": " + "<font color='" + MainApp.gc(R.color.tempTargetConfirmation) + "'>" + DecimalFormatter.to1Decimal(eatingSoonTT) + " mmol/l (" + eatingSoonTTDuration + " min)</font>");
                } else
                    actions.add(MainApp.gs(R.string.temptargetshort) + ": " + "<font color='" + MainApp.gc(R.color.tempTargetConfirmation) + "'>" + DecimalFormatter.to0Decimal(eatingSoonTT) + " mg/dl (" + eatingSoonTTDuration + " min)</font>");
            }

            int timeOffset = editTime.getValue().intValue();
            final long time = now() + T.mins(timeOffset).msecs();
            if (timeOffset != 0) {
                actions.add(MainApp.gs(R.string.time) + ": " + DateUtil.dateAndTimeString(time));
            }
            final String notes = notesEdit.getText().toString();
            if (!notes.isEmpty()) {
                actions.add(MainApp.gs(R.string.careportal_newnstreatment_notes_label) + ": " + notes);
            }

            final double finalInsulinAfterConstraints = insulinAfterConstraints;
            final double finalEatigSoonTT = eatingSoonTT;
            final int finalEatingSoonTTDuration = eatingSoonTTDuration;

            final Context context = getContext();
            final AlertDialog.Builder builder = new AlertDialog.Builder(context);

            builder.setTitle(MainApp.gs(R.string.confirmation));
            if (finalInsulinAfterConstraints > 0 || startEatingSoonTTCheckbox.isChecked()) {
                builder.setMessage(Html.fromHtml(Joiner.on("<br/>").join(actions)));
                builder.setPositiveButton(MainApp.gs(R.string.ok), (dialog, id) -> {
                    synchronized (builder) {
                        if (accepted) {
                            log.debug("guarding: already accepted");
                            return;
                        }
                        accepted = true;

                        if (startEatingSoonTTCheckbox.isChecked()) {
                            TempTarget tempTarget = new TempTarget()
                                    .date(System.currentTimeMillis())
                                    .duration(finalEatingSoonTTDuration)
                                    .reason(MainApp.gs(R.string.eatingsoon))
                                    .source(Source.USER)
                                    .low(Profile.toMgdl(finalEatigSoonTT, currentProfile.getUnits()))
                                    .high(Profile.toMgdl(finalEatigSoonTT, currentProfile.getUnits()));
                            TreatmentsPlugin.getPlugin().addToHistoryTempTarget(tempTarget);
                        }

                        if (finalInsulinAfterConstraints > 0) {
                            DetailedBolusInfo detailedBolusInfo = new DetailedBolusInfo();
                            detailedBolusInfo.eventType = CareportalEvent.CORRECTIONBOLUS;
                            detailedBolusInfo.insulin = finalInsulinAfterConstraints;
                            detailedBolusInfo.context = context;
                            detailedBolusInfo.source = Source.USER;
                            detailedBolusInfo.notes = notes;
                            if (recordOnlyCheckbox.isChecked()) {
                                detailedBolusInfo.date = time;
                                TreatmentsPlugin.getPlugin().addToHistoryTreatment(detailedBolusInfo, false);
                            } else {
                                detailedBolusInfo.date = now();
                                ConfigBuilderPlugin.getPlugin().getCommandQueue().bolus(detailedBolusInfo, new Callback() {
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
                            }
                        }
                    }
                });
            } else {
                builder.setMessage(MainApp.gs(R.string.no_action_selected));
            }
            builder.setNegativeButton(MainApp.gs(R.string.cancel), null);
            builder.show();
            dismiss();
        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }
    }
}
