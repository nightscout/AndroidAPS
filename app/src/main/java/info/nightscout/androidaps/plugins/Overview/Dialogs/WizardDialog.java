package info.nightscout.androidaps.plugins.Overview.Dialogs;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import java.text.DecimalFormat;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.interfaces.TempBasalsInterface;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.plugins.OpenAPSMA.IobTotal;
import info.nightscout.client.data.NSProfile;
import info.nightscout.utils.*;

public class WizardDialog extends DialogFragment implements OnClickListener {

    Button wizardDialogDeliverButton;
    TextView correctionInput;
    TextView carbsInput;
    TextView bgInput;
    TextView bg, bgInsulin, bgUnits;
    CheckBox bgCheckbox;
    TextView carbs, carbsInsulin;
    TextView bolusIobInsulin;
    TextView basalIobInsulin;
    CheckBox bolusIobCheckbox;
    CheckBox basalIobCheckbox;
    TextView correctionInsulin;
    TextView total, totalInsulin;

    PlusMinusEditText editBg;
    PlusMinusEditText editCarbs;
    PlusMinusEditText editCorr;

    public static final DecimalFormat numberFormat = new DecimalFormat("0.00");
    public static final DecimalFormat intFormat = new DecimalFormat("0");

    Integer calculatedCarbs = 0;
    Double calculatedTotalInsulin = 0d;

    final private TextWatcher textWatcher = new TextWatcher() {
        @Override
        public void afterTextChanged(Editable s) {
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            calculateInsulin();
        }
    };

    final CompoundButton.OnCheckedChangeListener onCheckedChangeListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            calculateInsulin();
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.overview_wizard_fragment, null, false);

        wizardDialogDeliverButton = (Button) view.findViewById(R.id.treatments_wizard_deliverButton);
        wizardDialogDeliverButton.setOnClickListener(this);

        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        correctionInput = (TextView) view.findViewById(R.id.treatments_wizard_correctioninput);
        carbsInput = (TextView) view.findViewById(R.id.treatments_wizard_carbsinput);
        bgInput = (TextView) view.findViewById(R.id.treatments_wizard_bginput);

        correctionInput.addTextChangedListener(textWatcher);
        carbsInput.addTextChangedListener(textWatcher);
        bgInput.addTextChangedListener(textWatcher);

        bg = (TextView) view.findViewById(R.id.treatments_wizard_bg);
        bgInsulin = (TextView) view.findViewById(R.id.treatments_wizard_bginsulin);
        bgUnits = (TextView) view.findViewById(R.id.treatments_wizard_bgunits);
        bgCheckbox = (CheckBox) view.findViewById(R.id.treatments_wizard_bgcheckbox);
        carbs = (TextView) view.findViewById(R.id.treatments_wizard_carbs);
        carbsInsulin = (TextView) view.findViewById(R.id.treatments_wizard_carbsinsulin);
        bolusIobInsulin = (TextView) view.findViewById(R.id.treatments_wizard_bolusiobinsulin);
        basalIobInsulin = (TextView) view.findViewById(R.id.treatments_wizard_basaliobinsulin);
        bolusIobCheckbox = (CheckBox) view.findViewById(R.id.treatments_wizard_bolusiobcheckbox);
        basalIobCheckbox = (CheckBox) view.findViewById(R.id.treatments_wizard_basaliobcheckbox);
        correctionInsulin = (TextView) view.findViewById(R.id.treatments_wizard_correctioninsulin);
        total = (TextView) view.findViewById(R.id.treatments_wizard_total);
        totalInsulin = (TextView) view.findViewById(R.id.treatments_wizard_totalinsulin);

        bgCheckbox.setOnCheckedChangeListener(onCheckedChangeListener);
        basalIobCheckbox.setOnCheckedChangeListener(onCheckedChangeListener);
        bolusIobCheckbox.setOnCheckedChangeListener(onCheckedChangeListener);

        Integer maxCarbs = MainApp.getConfigBuilder().applyCarbsConstraints(Constants.carbsOnlyForCheckLimit);
        Double maxCorrection = MainApp.getConfigBuilder().applyBolusConstraints(Constants.bolusOnlyForCheckLimit);

        editBg = new PlusMinusEditText(view, R.id.treatments_wizard_bginput, R.id.treatments_wizard_bginput_plus, R.id.treatments_wizard_bginput_minus, 0d, 0d, 500d, 0.1d, new DecimalFormat("0.0"), false);
        editCarbs = new PlusMinusEditText(view, R.id.treatments_wizard_carbsinput, R.id.treatments_wizard_carbsinput_plus, R.id.treatments_wizard_carbsinput_minus, 0d, 0d, (double) maxCarbs, 1d, new DecimalFormat("0"), false);
        editCorr = new PlusMinusEditText(view, R.id.treatments_wizard_correctioninput, R.id.treatments_wizard_correctioninput_plus, R.id.treatments_wizard_correctioninput_minus, 0d, 0d, maxCorrection, 0.05d, new DecimalFormat("0.00"), false);
        initDialog();
        return view;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.treatments_wizard_deliverButton:
                if (calculatedTotalInsulin > 0d || calculatedCarbs > 0d) {
                    DecimalFormat formatNumber2decimalplaces = new DecimalFormat("0.00");
                    String confirmMessage = getString(R.string.entertreatmentquestion);

                    Double insulinAfterConstraints = MainApp.getConfigBuilder().applyBolusConstraints(calculatedTotalInsulin);
                    Integer carbsAfterConstraints = MainApp.getConfigBuilder().applyCarbsConstraints(calculatedCarbs);

                    confirmMessage += "\n" + getString(R.string.bolus) + ": " + formatNumber2decimalplaces.format(insulinAfterConstraints) + "U";
                    confirmMessage += "\n" + getString(R.string.carbs) + ": " + carbsAfterConstraints + "g";

                    if (insulinAfterConstraints - calculatedTotalInsulin  != 0 || carbsAfterConstraints != calculatedCarbs) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                        builder.setTitle(getContext().getString(R.string.treatmentdeliveryerror));
                        builder.setMessage(getString(R.string.constraints_violation) + "\n" + getString(R.string.changeyourinput));
                        builder.setPositiveButton(getContext().getString(R.string.ok), null);
                        builder.show();
                        return;
                    }

                    final Double finalInsulinAfterConstraints = insulinAfterConstraints;
                    final Integer finalCarbsAfterConstraints = carbsAfterConstraints;

                    AlertDialog.Builder builder = new AlertDialog.Builder(this.getContext());
                    builder.setTitle(this.getContext().getString(R.string.confirmation));
                    builder.setMessage(confirmMessage);
                    builder.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            if (finalInsulinAfterConstraints > 0 || finalCarbsAfterConstraints > 0) {
                                PumpInterface pump = MainApp.getConfigBuilder().getActivePump();
                                PumpEnactResult result = pump.deliverTreatment(finalInsulinAfterConstraints, finalCarbsAfterConstraints);
                                if (!result.success) {
                                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                                    builder.setTitle(getContext().getString(R.string.treatmentdeliveryerror));
                                    builder.setMessage(result.comment);
                                    builder.setPositiveButton(getContext().getString(R.string.ok), null);
                                    builder.show();
                                }
                            }
                        }
                    });
                    builder.setNegativeButton(getString(R.string.cancel), null);
                    builder.show();
                    dismiss();
                }
                break;
        }

    }

    private void initDialog() {
        NSProfile profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();

        if (profile == null) {
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.resources.getString(R.string.noprofile));
            return;
        }

        String units = profile.getUnits();
        bgUnits.setText(units);
        if (units.equals(Constants.MGDL)) editBg.setStep(1d);
        else editBg.setStep(0.1d);

        // Set BG if not old
        BgReading lastBg = MainApp.getDbHelper().lastBg();

        if (lastBg != null) {
            Double lastBgValue = lastBg.valueToUnits(units);
            Double sens = profile.getIsf(NSProfile.secondsFromMidnight());
            Double targetBGLow = profile.getTargetLow(NSProfile.secondsFromMidnight());
            Double targetBGHigh = profile.getTargetHigh(NSProfile.secondsFromMidnight());
            Double bgDiff;
            if (lastBgValue <= targetBGLow) {
                bgDiff = lastBgValue - targetBGLow;
            } else {
                bgDiff = lastBgValue - targetBGHigh;
            }

            bg.setText(lastBg.valueToUnitsToString(units) + " ISF: " + intFormat.format(sens));
            bgInsulin.setText(numberFormat.format(bgDiff / sens) + "U");
            bgInput.removeTextChangedListener(textWatcher);
            //bgInput.setText(lastBg.valueToUnitsToString(units));
            editBg.setValue(lastBg.valueToUnits(units));
            bgInput.addTextChangedListener(textWatcher);
        } else {
            bg.setText("");
            bgInsulin.setText("");
            bgInput.removeTextChangedListener(textWatcher);
            //bgInput.setText("");
            editBg.setValue(0d);
            bgInput.addTextChangedListener(textWatcher);
        }

        // IOB calculation
        TreatmentsInterface treatments = MainApp.getConfigBuilder().getActiveTreatments();
        TempBasalsInterface tempBasals = MainApp.getConfigBuilder().getActiveTempBasals();
        treatments.updateTotalIOB();
        tempBasals.updateTotalIOB();
        IobTotal bolusIob = treatments.getLastCalculation();
        IobTotal basalIob = tempBasals.getLastCalculation();

        bolusIobInsulin.setText("-" + numberFormat.format(bolusIob.iob) + "U");
        basalIobInsulin.setText("-" + numberFormat.format(basalIob.basaliob) + "U");

        totalInsulin.setText("");
        wizardDialogDeliverButton.setVisibility(Button.INVISIBLE);

    }

    private void calculateInsulin() {
        NSProfile profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();

        // Entered values
        Double c_bg = SafeParse.stringToDouble(this.bgInput.getText().toString());
        Integer c_carbs = SafeParse.stringToInt(this.carbsInput.getText().toString());
        Double c_correction = SafeParse.stringToDouble(this.correctionInput.getText().toString());
        Double corrAfterConstraint = MainApp.getConfigBuilder().applyBolusConstraints(c_correction);
        if (c_correction - corrAfterConstraint != 0) { // c_correction != corrAfterConstraint doesn't work
            correctionInput.removeTextChangedListener(textWatcher);
            correctionInput.setText("");
            correctionInput.addTextChangedListener(textWatcher);
            //wizardDialogDeliverButton.setVisibility(Button.GONE);
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), getString(R.string.bolusconstraintapplied));
            return;
        }
        Integer carbsAfterConstraint = MainApp.getConfigBuilder().applyCarbsConstraints(c_carbs);
        if (c_carbs != carbsAfterConstraint) {
            carbsInput.removeTextChangedListener(textWatcher);
            carbsInput.setText("");
            carbsInput.addTextChangedListener(textWatcher);
            //wizardDialogDeliverButton.setVisibility(Button.GONE);
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), getString(R.string.carbsconstraintapplied));
            return;
        }


        // Insulin from BG
        Double sens = profile.getIsf(NSProfile.secondsFromMidnight());
        Double targetBGLow = profile.getTargetLow(NSProfile.secondsFromMidnight());
        Double targetBGHigh = profile.getTargetHigh(NSProfile.secondsFromMidnight());
        Double bgDiff;
        if (c_bg <= targetBGLow) {
            bgDiff = c_bg - targetBGLow;
        } else {
            bgDiff = c_bg - targetBGHigh;
        }
        Double insulinFromBG = (bgCheckbox.isChecked() && c_bg != 0d) ? bgDiff / sens : 0d;
        bg.setText(c_bg + " ISF: " + intFormat.format(sens));
        bgInsulin.setText(numberFormat.format(insulinFromBG) + "U");

        // Insuling from carbs
        Double ic = profile.getIc(NSProfile.secondsFromMidnight());
        Double insulinFromCarbs = c_carbs / ic;
        carbs.setText(intFormat.format(c_carbs) + "g IC: " + intFormat.format(ic));
        carbsInsulin.setText(numberFormat.format(insulinFromCarbs) + "U");

        // Insulin from IOB
        TreatmentsInterface treatments = MainApp.getConfigBuilder().getActiveTreatments();
        TempBasalsInterface tempBasals = MainApp.getConfigBuilder().getActiveTempBasals();
        treatments.updateTotalIOB();
        tempBasals.updateTotalIOB();
        IobTotal bolusIob = treatments.getLastCalculation();
        IobTotal basalIob = tempBasals.getLastCalculation();

        Double insulingFromBolusIOB = bolusIobCheckbox.isChecked() ? -bolusIob.iob : 0d;
        Double insulingFromBasalsIOB = basalIobCheckbox.isChecked() ? -basalIob.basaliob : 0d;
        bolusIobInsulin.setText(numberFormat.format(insulingFromBolusIOB) + "U");
        basalIobInsulin.setText(numberFormat.format(insulingFromBasalsIOB) + "U");

        // Insulin from correction
        Double insulinFromCorrection = corrAfterConstraint;
        correctionInsulin.setText(numberFormat.format(insulinFromCorrection) + "U");

        // Total
        calculatedTotalInsulin = insulinFromBG + insulinFromCarbs + insulingFromBolusIOB + insulingFromBasalsIOB + insulinFromCorrection;

        if (calculatedTotalInsulin < 0) {
            Double carbsEquivalent = -calculatedTotalInsulin * ic;
            total.setText(getString(R.string.missing) + " " + intFormat.format(carbsEquivalent) + "g");
            calculatedTotalInsulin = 0d;
            totalInsulin.setText("");
        } else {
            calculatedTotalInsulin = Round.roundTo(calculatedTotalInsulin, 0.05d);
            total.setText("");
            totalInsulin.setText(numberFormat.format(calculatedTotalInsulin) + "U");
        }

        calculatedCarbs = c_carbs;

        if (calculatedTotalInsulin > 0d || calculatedCarbs > 0d) {
            String insulinText = calculatedTotalInsulin > 0d ? (numberFormat.format(calculatedTotalInsulin) + "U") : "";
            String carbsText = calculatedCarbs > 0d ? (intFormat.format(calculatedCarbs) + "g") : "";
            wizardDialogDeliverButton.setText(getString(R.string.send) + " " + insulinText + " " + carbsText);
            wizardDialogDeliverButton.setVisibility(Button.VISIBLE);
        } else {
            wizardDialogDeliverButton.setVisibility(Button.INVISIBLE);
        }
    }
}