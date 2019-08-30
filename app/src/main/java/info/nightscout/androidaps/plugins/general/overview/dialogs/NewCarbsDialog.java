package info.nightscout.androidaps.plugins.general.overview.dialogs;

import android.os.Bundle;
import android.os.HandlerThread;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;
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
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;

import com.google.common.base.Joiner;

import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.LinkedList;
import java.util.List;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.plugins.treatments.CarbsGenerator;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.DefaultValueHelper;
import info.nightscout.androidaps.utils.NumberPicker;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.ToastUtils;

import static info.nightscout.androidaps.utils.DateUtil.now;

public class NewCarbsDialog extends DialogFragment implements OnClickListener, CompoundButton.OnCheckedChangeListener {
    private static Logger log = LoggerFactory.getLogger(NewCarbsDialog.class);

    private static final int FAV1_DEFAULT = 5;
    private static final int FAV2_DEFAULT = 10;
    private static final int FAV3_DEFAULT = 20;

    private RadioButton startActivityTTCheckbox;
    private RadioButton startEatingSoonTTCheckbox;
    private RadioButton startHypoTTCheckbox;
    private boolean togglingTT;

    private NumberPicker editTime;
    private NumberPicker editDuration;
    private NumberPicker editCarbs;
    private Integer maxCarbs;

    private EditText notesEdit;

    //one shot guards
    private boolean accepted;
    private boolean okClicked;

    public NewCarbsDialog() {
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
        if (time > 12 * 60 || time < -12 * 60) {
            editTime.setValue(0d);
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.gs(R.string.constraintapllied));
        }
        Double duration = editDuration.getValue();
        if (duration > 10) {
            editDuration.setValue(0d);
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.gs(R.string.constraintapllied));
        }
        int carbs = editCarbs.getValue().intValue();
        if (carbs > maxCarbs) {
            editCarbs.setValue(0d);
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.gs(R.string.carbsconstraintapplied));
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.overview_newcarbs_dialog, container, false);

        view.findViewById(R.id.ok).setOnClickListener(this);
        view.findViewById(R.id.cancel).setOnClickListener(this);

        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        startActivityTTCheckbox = view.findViewById(R.id.newcarbs_activity_tt);
        startActivityTTCheckbox.setOnCheckedChangeListener(this);
        startEatingSoonTTCheckbox = view.findViewById(R.id.newcarbs_eating_soon_tt);
        startEatingSoonTTCheckbox.setOnCheckedChangeListener(this);
        startHypoTTCheckbox = view.findViewById(R.id.newcarbs_hypo_tt);

        editTime = view.findViewById(R.id.newcarbs_time);
        editTime.setParams(0d, -12 * 60d, 12 * 60d, 5d, new DecimalFormat("0"), false, view.findViewById(R.id.ok), textWatcher);

        editDuration = view.findViewById(R.id.new_carbs_duration);
        editDuration.setParams(0d, 0d, 10d, 1d, new DecimalFormat("0"), false, view.findViewById(R.id.ok), textWatcher);

        maxCarbs = MainApp.getConstraintChecker().getMaxCarbsAllowed().value();

        editCarbs = view.findViewById(R.id.newcarb_carbsamount);
        editCarbs.setParams(0d, 0d, (double) maxCarbs, 1d, new DecimalFormat("0"), false, view.findViewById(R.id.ok), textWatcher);

        Button fav1Button = view.findViewById(R.id.newcarbs_plus1);
        fav1Button.setOnClickListener(this);
        fav1Button.setText(toSignedString(SP.getInt(R.string.key_carbs_button_increment_1, FAV1_DEFAULT)));

        Button fav2Button = view.findViewById(R.id.newcarbs_plus2);
        fav2Button.setOnClickListener(this);
        fav2Button.setText(toSignedString(SP.getInt(R.string.key_carbs_button_increment_2, FAV2_DEFAULT)));

        Button fav3Button = view.findViewById(R.id.newcarbs_plus3);
        fav3Button.setOnClickListener(this);
        fav3Button.setText(toSignedString(SP.getInt(R.string.key_carbs_button_increment_3, FAV3_DEFAULT)));

        LinearLayout notesLayout = view.findViewById(R.id.newcarbs_notes_layout);
        notesLayout.setVisibility(SP.getBoolean(R.string.key_show_notes_entry_dialogs, false) ? View.VISIBLE : View.GONE);
        notesEdit = view.findViewById(R.id.newcarbs_notes);

        BgReading bgReading = DatabaseHelper.actualBg();
        if (bgReading != null && bgReading.value < 72) {
            startHypoTTCheckbox.setChecked(true);
            // see #onCheckedChanged why listeners are registered like this
            startHypoTTCheckbox.setOnClickListener(this);
        } else {
            startHypoTTCheckbox.setOnCheckedChangeListener(this);
        }

        setCancelable(true);
        getDialog().setCanceledOnTouchOutside(false);

        //recovering state if there is something
        if (savedInstanceState != null) {
            editCarbs.setValue(savedInstanceState.getDouble("editCarbs"));
            editTime.setValue(savedInstanceState.getDouble("editTime"));
            editDuration.setValue(savedInstanceState.getDouble("editDuration"));
        }
        return view;
    }

    private String toSignedString(int value) {
        return value > 0 ? "+" + value : String.valueOf(value);
    }


    @Override
    public void onSaveInstanceState(Bundle carbsDialogState) {
        carbsDialogState.putBoolean("startActivityTTCheckbox",startActivityTTCheckbox.isChecked());
        carbsDialogState.putBoolean("startEatingSoonTTCheckbox", startEatingSoonTTCheckbox.isChecked());
        carbsDialogState.putBoolean("startHypoTTCheckbox", startHypoTTCheckbox.isChecked());
        carbsDialogState.putDouble("editTime", editTime.getValue());
        carbsDialogState.putDouble("editDuration", editDuration.getValue());
        carbsDialogState.putDouble("editCarbs", editCarbs.getValue());
        super.onSaveInstanceState(carbsDialogState);
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
            case R.id.newcarbs_plus1:
                editCarbs.setValue(Math.max(0, editCarbs.getValue()
                        + SP.getInt(R.string.key_carbs_button_increment_1, FAV1_DEFAULT)));
                validateInputs();
                break;
            case R.id.newcarbs_plus2:
                editCarbs.setValue(Math.max(0, editCarbs.getValue()
                        + SP.getInt(R.string.key_carbs_button_increment_2, FAV2_DEFAULT)));
                validateInputs();
                break;
            case R.id.newcarbs_plus3:
                editCarbs.setValue(Math.max(0, editCarbs.getValue()
                        + SP.getInt(R.string.key_carbs_button_increment_3, FAV3_DEFAULT)));
                validateInputs();
                break;
            case R.id.newcarbs_activity_tt:
                if (togglingTT) {
                    togglingTT = false;
                    break;
                }
                startActivityTTCheckbox.setOnClickListener(null);
                startActivityTTCheckbox.setOnCheckedChangeListener(null);
                startActivityTTCheckbox.setChecked(false);
                startActivityTTCheckbox.setOnCheckedChangeListener(this);
                break;
            case R.id.newcarbs_eating_soon_tt:
                if (togglingTT) {
                    togglingTT = false;
                    break;
                }
                startEatingSoonTTCheckbox.setOnClickListener(null);
                startEatingSoonTTCheckbox.setOnCheckedChangeListener(null);
                startEatingSoonTTCheckbox.setChecked(false);
                startEatingSoonTTCheckbox.setOnCheckedChangeListener(this);
                break;
            case R.id.newcarbs_hypo_tt:
                if (togglingTT) {
                    togglingTT = false;
                    break;
                }
                startHypoTTCheckbox.setOnClickListener(null);
                startHypoTTCheckbox.setOnCheckedChangeListener(null);
                startHypoTTCheckbox.setChecked(false);
                startHypoTTCheckbox.setOnCheckedChangeListener(this);
                break;
        }
    }



    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        // Logic to disable a selected radio when pressed: when a checked radio
        // is pressed, no CheckChanged event is triggered, so register a Click event
        // when checking a radio. Since Click events come after CheckChanged events,
        // the Click event is triggered immediately after this. Thus, set togglingTT
        // var to true, so that the first Click event fired after this is ignored.
        // Radios remove themselves from Click events once unchecked.
        // Since radios are not in a group,  their state is manually updated here.
        switch (buttonView.getId()) {
            case R.id.newcarbs_activity_tt:
                togglingTT = true;
                startActivityTTCheckbox.setOnClickListener(this);

                startEatingSoonTTCheckbox.setOnCheckedChangeListener(null);
                startEatingSoonTTCheckbox.setChecked(false);
                startEatingSoonTTCheckbox.setOnCheckedChangeListener(this);

                startHypoTTCheckbox.setOnCheckedChangeListener(null);
                startHypoTTCheckbox.setChecked(false);
                startHypoTTCheckbox.setOnCheckedChangeListener(this);
                break;
            case R.id.newcarbs_eating_soon_tt:
                togglingTT = true;
                startEatingSoonTTCheckbox.setOnClickListener(this);

                startActivityTTCheckbox.setOnCheckedChangeListener(null);
                startActivityTTCheckbox.setChecked(false);
                startActivityTTCheckbox.setOnCheckedChangeListener(this);

                startHypoTTCheckbox.setOnCheckedChangeListener(null);
                startHypoTTCheckbox.setChecked(false);
                startHypoTTCheckbox.setOnCheckedChangeListener(this);
                break;
            case R.id.newcarbs_hypo_tt:
                togglingTT = true;
                startHypoTTCheckbox.setOnClickListener(this);

                startActivityTTCheckbox.setOnCheckedChangeListener(null);
                startActivityTTCheckbox.setChecked(false);
                startActivityTTCheckbox.setOnCheckedChangeListener(this);

                startEatingSoonTTCheckbox.setOnCheckedChangeListener(null);
                startEatingSoonTTCheckbox.setChecked(false);
                startEatingSoonTTCheckbox.setOnCheckedChangeListener(this);
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
            final Profile currentProfile = ProfileFunctions.getInstance().getProfile();
            if (currentProfile == null) {
                return;
            }

            int carbs = editCarbs.getValue().intValue();
            Integer carbsAfterConstraints = MainApp.getConstraintChecker().applyCarbsConstraints(new Constraint<>(carbs)).value();

            final String units = currentProfile.getUnits();
            DefaultValueHelper helper = new DefaultValueHelper();

            int activityTTDuration = helper.determineActivityTTDuration();
            double activityTT = helper.determineActivityTT(units);

            int eatingSoonTTDuration = helper.determineEatingSoonTTDuration();
            double eatingSoonTT = helper.determineEatingSoonTT(units);

            int hypoTTDuration = helper.determineHypoTTDuration();
            double hypoTT = helper.determineHypoTT(units);

            List<String> actions = new LinkedList<>();

            if (startActivityTTCheckbox.isChecked()) {
                String unitLabel = "mg/dl";
                if (currentProfile.getUnits().equals(Constants.MMOL)) {
                    unitLabel = "mmol/l";
                }
                actions.add(MainApp.gs(R.string.temptargetshort) + ": " + "<font color='" + MainApp.gc(R.color.tempTargetConfirmation) + "'>" + DecimalFormatter.to1Decimal(activityTT) + " " + unitLabel + " (" + activityTTDuration + " min)</font>");
            }
            if (startEatingSoonTTCheckbox.isChecked()) {
                if (currentProfile.getUnits().equals(Constants.MMOL)) {
                    actions.add(MainApp.gs(R.string.temptargetshort) + ": " + "<font color='" + MainApp.gc(R.color.tempTargetConfirmation) + "'>" + DecimalFormatter.to1Decimal(eatingSoonTT) + " mmol/l (" + eatingSoonTTDuration + " min)</font>");
                } else {
                    actions.add(MainApp.gs(R.string.temptargetshort) + ": " + "<font color='" + MainApp.gc(R.color.tempTargetConfirmation) + "'>" + DecimalFormatter.to0Decimal(eatingSoonTT) + " mg/dl (" + eatingSoonTTDuration + " min)</font>");
                }
            }
            if (startHypoTTCheckbox.isChecked()) {
                if (currentProfile.getUnits().equals(Constants.MMOL)) {
                    actions.add(MainApp.gs(R.string.temptargetshort) + ": " + "<font color='" + MainApp.gc(R.color.tempTargetConfirmation) + "'>" + DecimalFormatter.to1Decimal(hypoTT) + " mmol/l (" + hypoTTDuration + " min)</font>");
                } else {
                    actions.add(MainApp.gs(R.string.temptargetshort) + ": " + "<font color='" + MainApp.gc(R.color.tempTargetConfirmation) + "'>" + DecimalFormatter.to0Decimal(hypoTT) + " mg/dl (" + hypoTTDuration + " min)</font>");
                }
            }

            int timeOffset = editTime.getValue().intValue();
            final long time = now() + timeOffset * 1000 * 60;
            if (timeOffset != 0) {
                actions.add(MainApp.gs(R.string.time) + ": " + DateUtil.dateAndTimeString(time));
            }
            int duration = editDuration.getValue().intValue();
            if (duration > 0) {
                actions.add(MainApp.gs(R.string.duration) + ": " + duration + MainApp.gs(R.string.shorthour));
            }
            if (carbs > 0) {
                actions.add(MainApp.gs(R.string.carbs) + ": " + "<font color='" + MainApp.gc(R.color.carbs) + "'>" + carbsAfterConstraints + "g" + "</font>");
            }
            if (!carbsAfterConstraints.equals(carbs)) {
                actions.add("<font color='" + MainApp.gc(R.color.warning) + "'>" + MainApp.gs(R.string.carbsconstraintapplied) + "</font>");
            }
            final String notes = notesEdit.getText().toString();
            if (!notes.isEmpty()) {
                actions.add(MainApp.gs(R.string.careportal_newnstreatment_notes_label) + ": " + notes);
            }

            final double finalActivityTT = activityTT;
            final int finalActivityTTDuration = activityTTDuration;
            final double finalEatigSoonTT = eatingSoonTT;
            final int finalEatingSoonTTDuration = eatingSoonTTDuration;
            final double finalHypoTT = hypoTT;
            final int finalHypoTTDuration = hypoTTDuration;

            final AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle(MainApp.gs(R.string.confirmation));
            if (carbsAfterConstraints > 0 || startActivityTTCheckbox.isChecked()
                    || startEatingSoonTTCheckbox.isChecked() || startHypoTTCheckbox.isChecked()) {
                builder.setMessage(Html.fromHtml(Joiner.on("<br/>").join(actions)));
                builder.setPositiveButton(MainApp.gs(R.string.ok), (dialog, id) -> {
                    synchronized (builder) {
                        if (accepted) {
                            log.debug("guarding: already accepted");
                            return;
                        }
                        accepted = true;

                        if (startActivityTTCheckbox.isChecked()) {
                            TempTarget tempTarget = new TempTarget()
                                    .date(System.currentTimeMillis())
                                    .duration(finalActivityTTDuration)
                                    .reason(MainApp.gs(R.string.activity))
                                    .source(Source.USER)
                                    .low(Profile.toMgdl(finalActivityTT, currentProfile.getUnits()))
                                    .high(Profile.toMgdl(finalActivityTT, currentProfile.getUnits()));
                            TreatmentsPlugin.getPlugin().addToHistoryTempTarget(tempTarget);
                        } else if (startEatingSoonTTCheckbox.isChecked()) {
                            TempTarget tempTarget = new TempTarget()
                                    .date(System.currentTimeMillis())
                                    .duration(finalEatingSoonTTDuration)
                                    .reason(MainApp.gs(R.string.eatingsoon))
                                    .source(Source.USER)
                                    .low(Profile.toMgdl(finalEatigSoonTT, currentProfile.getUnits()))
                                    .high(Profile.toMgdl(finalEatigSoonTT, currentProfile.getUnits()));
                            TreatmentsPlugin.getPlugin().addToHistoryTempTarget(tempTarget);
                        } else if (startHypoTTCheckbox.isChecked()) {
                            TempTarget tempTarget = new TempTarget()
                                    .date(System.currentTimeMillis())
                                    .duration(finalHypoTTDuration)
                                    .reason(MainApp.gs(R.string.hypo))
                                    .source(Source.USER)
                                    .low(Profile.toMgdl(finalHypoTT, currentProfile.getUnits()))
                                    .high(Profile.toMgdl(finalHypoTT, currentProfile.getUnits()));
                            TreatmentsPlugin.getPlugin().addToHistoryTempTarget(tempTarget);
                        }

                        if (carbsAfterConstraints > 0) {
                            if (duration == 0) {
                                CarbsGenerator.createCarb(carbsAfterConstraints, time, CareportalEvent.CARBCORRECTION, notes);
                            } else {
                                CarbsGenerator.generateCarbs(carbsAfterConstraints, time, duration, notes);
                                NSUpload.uploadEvent(CareportalEvent.NOTE, now() - 2000, MainApp.gs(R.string.generated_ecarbs_note, carbsAfterConstraints, duration, timeOffset));
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
