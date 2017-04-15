package info.nightscout.androidaps.plugins.Overview.Dialogs;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.squareup.otto.Subscribe;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Text;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnCheckedChanged;
import butterknife.OnClick;
import butterknife.OnItemSelected;
import butterknife.OnTextChanged;
import butterknife.Unbinder;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.GlucoseStatus;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.events.EventNewBG;
import info.nightscout.androidaps.events.EventRefreshGui;
import info.nightscout.androidaps.interfaces.TempBasalsInterface;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.plugins.Loop.APSResult;
import info.nightscout.androidaps.plugins.Loop.LoopPlugin;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSProfile;
import info.nightscout.androidaps.plugins.OpenAPSAMA.DetermineBasalResultAMA;
import info.nightscout.androidaps.plugins.OpenAPSAMA.OpenAPSAMAPlugin;
import info.nightscout.androidaps.plugins.OpenAPSMA.events.EventOpenAPSUpdateGui;
import info.nightscout.utils.BolusWizard;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.PlusMinusEditText;
import info.nightscout.utils.SafeParse;
import info.nightscout.utils.ToastUtils;

import static butterknife.OnItemSelected.Callback.NOTHING_SELECTED;

public class WizardDialog extends DialogFragment {
    private static Logger log = LoggerFactory.getLogger(WizardDialog.class);

    @BindView(R.id.treatments_wizard_deliverButton)
    Button wizardDialogDeliverButton;
    @BindView(R.id.treatments_wizard_correctioninput)
    TextView correctionInput;
    @BindView(R.id.treatments_wizard_carbsinput)
    TextView carbsInput;
    @BindView(R.id.treatments_wizard_bginput)
    TextView bgInput;
    @BindView(R.id.treatments_wizard_bg)
    TextView bg;
    @BindView(R.id.treatments_wizard_bginsulin)
    TextView bgInsulin;
    @BindView(R.id.treatments_wizard_bgunits)
    TextView bgUnits;
    @BindView(R.id.treatments_wizard_bgcheckbox)
    CheckBox bgCheckbox;
    @BindView(R.id.treatments_wizard_carbs)
    TextView carbs;
    @BindView(R.id.treatments_wizard_carbsinsulin)
    TextView carbsInsulin;
    @BindView(R.id.treatments_wizard_bolusiobinsulin)
    TextView bolusIobInsulin;
    @BindView(R.id.treatments_wizard_basaliobinsulin)
    TextView basalIobInsulin;
    @BindView(R.id.treatments_wizard_bolusiobcheckbox)
    CheckBox bolusIobCheckbox;
    @BindView(R.id.treatments_wizard_basaliobcheckbox)
    CheckBox basalIobCheckbox;
    @BindView(R.id.treatments_wizard_correctioninsulin)
    TextView correctionInsulin;
    @BindView(R.id.treatments_wizard_total)
    TextView total;
    @BindView(R.id.treatments_wizard_totalinsulin)
    TextView totalInsulin;
    @BindView(R.id.treatments_wizard_carbtimeinput)
    EditText carbTimeEdit;
    @BindView(R.id.treatments_wizard_profile)
    Spinner profileSpinner;
    @BindView(R.id.treatments_wizard_sbcheckbox)
    CheckBox superbolusCheckbox;
    @BindView(R.id.treatments_wizard_sb)
    TextView superbolus;
    @BindView(R.id.treatments_wizard_sbinsulin)
    TextView superbolusInsulin;
    @BindView(R.id.treatments_wizard_bgtrendcheckbox)
    CheckBox bgtrendCheckbox;
    @BindView(R.id.treatments_wizard_bgtrend)
    TextView bgTrend;
    @BindView(R.id.treatments_wizard_bgtrendinsulin)
    TextView bgTrendInsulin;
    @BindView(R.id.treatments_wizard_cob_layout)
    LinearLayout cobLayout;
    @BindView(R.id.treatments_wizard_cobcheckbox)
    CheckBox cobCheckbox;
    @BindView(R.id.treatments_wizard_cob)
    TextView cob;
    @BindView(R.id.treatments_wizard_cobinsulin)
    TextView cobInsulin;

    PlusMinusEditText editBg;
    PlusMinusEditText editCarbs;
    PlusMinusEditText editCorr;
    PlusMinusEditText editCarbTime;

    private Unbinder unbinder;

    Integer calculatedCarbs = 0;
    Double calculatedTotalInsulin = 0d;
    JSONObject boluscalcJSON;
    boolean cobAvailable = false;

    Handler mHandler;
    public static HandlerThread mHandlerThread;

    Context context;

    public WizardDialog() {
        super();
        mHandlerThread = new HandlerThread(WizardDialog.class.getSimpleName());
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        this.context = context;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unbinder.unbind();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getDialog() != null)
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        MainApp.bus().register(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        MainApp.bus().unregister(this);
    }

    @Subscribe
    public void onStatusEvent(final EventOpenAPSUpdateGui e) {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (ConfigBuilderPlugin.getActiveAPS() instanceof OpenAPSAMAPlugin && ConfigBuilderPlugin.getActiveAPS().getLastAPSResult() != null && ConfigBuilderPlugin.getActiveAPS().getLastAPSRun().after(new Date(new Date().getTime() - 11 * 60 * 1000L))) {
                        cobLayout.setVisibility(View.VISIBLE);
                        cobAvailable = true;
                    } else {
                        cobLayout.setVisibility(View.GONE);
                        cobAvailable = false;
                    }
                    calculateInsulin();
                }
            });
    }

    @Subscribe
    public void onStatusEvent(final EventNewBG e) {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    calculateInsulin();
                }
            });
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
            calculateInsulin();
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.overview_wizard_dialog, null, false);

        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        unbinder = ButterKnife.bind(this, view);

        correctionInput.addTextChangedListener(textWatcher);
        carbsInput.addTextChangedListener(textWatcher);
        bgInput.addTextChangedListener(textWatcher);

        Integer maxCarbs = MainApp.getConfigBuilder().applyCarbsConstraints(Constants.carbsOnlyForCheckLimit);
        Double maxCorrection = MainApp.getConfigBuilder().applyBolusConstraints(Constants.bolusOnlyForCheckLimit);

        editBg = new PlusMinusEditText(view, R.id.treatments_wizard_bginput, R.id.treatments_wizard_bginput_plus, R.id.treatments_wizard_bginput_minus, 0d, 0d, 500d, 0.1d, new DecimalFormat("0.0"), false);
        editCarbs = new PlusMinusEditText(view, R.id.treatments_wizard_carbsinput, R.id.treatments_wizard_carbsinput_plus, R.id.treatments_wizard_carbsinput_minus, 0d, 0d, (double) maxCarbs, 1d, new DecimalFormat("0"), false);
        editCorr = new PlusMinusEditText(view, R.id.treatments_wizard_correctioninput, R.id.treatments_wizard_correctioninput_plus, R.id.treatments_wizard_correctioninput_minus, 0d, -maxCorrection, maxCorrection, 0.05d, new DecimalFormat("0.00"), false);
        editCarbTime = new PlusMinusEditText(view, R.id.treatments_wizard_carbtimeinput, R.id.treatments_wizard_carbtime_plus, R.id.treatments_wizard_carbtime_minus, 0d, -60d, 60d, 5d, new DecimalFormat("0"), false);
        initDialog();

        return view;
    }

    @OnCheckedChanged({R.id.treatments_wizard_bgcheckbox, R.id.treatments_wizard_bolusiobcheckbox, R.id.treatments_wizard_basaliobcheckbox, R.id.treatments_wizard_sbcheckbox, R.id.treatments_wizard_cobcheckbox, R.id.treatments_wizard_bgtrendcheckbox})
    public void checkboxToggled(boolean isChecked) {
        calculateInsulin();
    }

    @OnItemSelected(R.id.treatments_wizard_profile)
    public void profileSelected(int position) {
        calculateInsulin();
        wizardDialogDeliverButton.setVisibility(View.VISIBLE);
    }

    @OnItemSelected(value = R.id.treatments_wizard_profile,
            callback = NOTHING_SELECTED)
    public void profileNotSelected() {
        ToastUtils.showToastInUiThread(context, MainApp.sResources.getString(R.string.noprofileselected));
        wizardDialogDeliverButton.setVisibility(View.GONE);
    }

    @OnClick(R.id.treatments_wizard_deliverButton)
    public void onClick(View view) {
        if (calculatedTotalInsulin > 0d || calculatedCarbs > 0d) {
            DecimalFormat formatNumber2decimalplaces = new DecimalFormat("0.00");
            String confirmMessage = getString(R.string.entertreatmentquestion);

            Double insulinAfterConstraints = MainApp.getConfigBuilder().applyBolusConstraints(calculatedTotalInsulin);
            Integer carbsAfterConstraints = MainApp.getConfigBuilder().applyCarbsConstraints(calculatedCarbs);

            confirmMessage += "\n" + getString(R.string.bolus) + ": " + formatNumber2decimalplaces.format(insulinAfterConstraints) + "U";
            confirmMessage += "\n" + getString(R.string.carbs) + ": " + carbsAfterConstraints + "g";

            if (insulinAfterConstraints - calculatedTotalInsulin != 0 || !carbsAfterConstraints.equals(calculatedCarbs)) {
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle(MainApp.sResources.getString(R.string.treatmentdeliveryerror));
                builder.setMessage(getString(R.string.constraints_violation) + "\n" + getString(R.string.changeyourinput));
                builder.setPositiveButton(MainApp.sResources.getString(R.string.ok), null);
                builder.show();
                return;
            }

            final Double finalInsulinAfterConstraints = insulinAfterConstraints;
            final Integer finalCarbsAfterConstraints = carbsAfterConstraints;
            final Double bg = SafeParse.stringToDouble(bgInput.getText().toString());
            final int carbTime = SafeParse.stringToInt(carbTimeEdit.getText().toString());
            final boolean useSuperBolus = superbolusCheckbox.isChecked();

            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(MainApp.sResources.getString(R.string.confirmation));
            builder.setMessage(confirmMessage);
            builder.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    if (finalInsulinAfterConstraints > 0 || finalCarbsAfterConstraints > 0) {
                        final ConfigBuilderPlugin pump = MainApp.getConfigBuilder();
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                PumpEnactResult result = pump.deliverTreatmentFromBolusWizard(
                                        context,
                                        finalInsulinAfterConstraints,
                                        finalCarbsAfterConstraints,
                                        bg,
                                        "Manual",
                                        carbTime,
                                        boluscalcJSON
                                );
                                if (!result.success) {
                                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                                    builder.setTitle(MainApp.sResources.getString(R.string.treatmentdeliveryerror));
                                    builder.setMessage(result.comment);
                                    builder.setPositiveButton(MainApp.sResources.getString(R.string.ok), null);
                                    builder.show();
                                }
                                if (useSuperBolus) {
                                    final LoopPlugin activeloop = MainApp.getConfigBuilder().getActiveLoop();
                                    result = pump.setTempBasalAbsolute(0d, 120);
                                    if (!result.success) {
                                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                                        builder.setTitle(MainApp.sResources.getString(R.string.tempbasaldeliveryerror));
                                        builder.setMessage(result.comment);
                                        builder.setPositiveButton(MainApp.sResources.getString(R.string.ok), null);
                                        builder.show();
                                    }
                                    if (activeloop != null) {
                                        activeloop.superBolusTo(new Date().getTime() + 2 * 60L * 60 * 1000);
                                        MainApp.bus().post(new EventRefreshGui(false));
                                    }
                                }
                            }
                        });
                        Answers.getInstance().logCustom(new CustomEvent("Wizard"));
                    }
                }
            });
            builder.setNegativeButton(getString(R.string.cancel), null);
            builder.show();
            dismiss();
        }
    }

    private void initDialog() {
        NSProfile profile = ConfigBuilderPlugin.getActiveProfile().getProfile();

        if (profile == null) {
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.noprofile));
            return;
        }

        ArrayList<CharSequence> profileList;
        profileList = profile.getProfileList();
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(getContext(),
                android.R.layout.simple_spinner_item, profileList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        profileSpinner.setAdapter(adapter);
        // set selected to actual profile
        for (int p = 0; p < profileList.size(); p++) {
            if (profileList.get(p).equals(profile.getActiveProfile()))
                profileSpinner.setSelection(p);
        }

        String units = profile.getUnits();
        bgUnits.setText(units);
        if (units.equals(Constants.MGDL)) editBg.setStep(1d);
        else editBg.setStep(0.1d);

        // Set BG if not old
        BgReading lastBg = GlucoseStatus.actualBg();

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

            bg.setText(lastBg.valueToUnitsToString(units) + " ISF: " + DecimalFormatter.to1Decimal(sens));
            bgInsulin.setText(DecimalFormatter.to2Decimal(bgDiff / sens) + "U");
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
        TreatmentsInterface treatments = ConfigBuilderPlugin.getActiveTreatments();
        treatments.updateTotalIOB();
        IobTotal bolusIob = treatments.getLastCalculation();
        TempBasalsInterface tempBasals = ConfigBuilderPlugin.getActiveTempBasals();
        IobTotal basalIob = new IobTotal(new Date().getTime());
        if (tempBasals != null) {
            tempBasals.updateTotalIOB();
            basalIob = tempBasals.getLastCalculation().round();
        }

        bolusIobInsulin.setText(DecimalFormatter.to2Decimal(-bolusIob.iob) + "U");
        basalIobInsulin.setText(DecimalFormatter.to2Decimal(-basalIob.basaliob) + "U");

        totalInsulin.setText("");
        wizardDialogDeliverButton.setVisibility(Button.INVISIBLE);

        // COB only if AMA is selected
        if (ConfigBuilderPlugin.getActiveAPS() instanceof OpenAPSAMAPlugin && ConfigBuilderPlugin.getActiveAPS().getLastAPSResult() != null && ConfigBuilderPlugin.getActiveAPS().getLastAPSRun().after(new Date(new Date().getTime() - 11 * 60 * 1000L))) {
            cobLayout.setVisibility(View.VISIBLE);
            cobAvailable = true;
        } else {
            cobLayout.setVisibility(View.GONE);
            cobAvailable = false;
        }
    }

    private void calculateInsulin() {
        NSProfile profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();
        if (profileSpinner == null || profileSpinner.getSelectedItem() == null)
            return; // not initialized yet
        String selectedAlternativeProfile = profileSpinner.getSelectedItem().toString();
        JSONObject specificProfile = profile.getSpecificProfile(selectedAlternativeProfile);

        // Entered values
        Double c_bg = SafeParse.stringToDouble(bgInput.getText().toString());
        Integer c_carbs = SafeParse.stringToInt(carbsInput.getText().toString());
        Double c_correction = SafeParse.stringToDouble(correctionInput.getText().toString());
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
        if (c_carbs - carbsAfterConstraint != 0) {
            carbsInput.removeTextChangedListener(textWatcher);
            carbsInput.setText("");
            carbsInput.addTextChangedListener(textWatcher);
            //wizardDialogDeliverButton.setVisibility(Button.GONE);
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), getString(R.string.carbsconstraintapplied));
            return;
        }

        c_bg = bgCheckbox.isChecked() ? c_bg : 0d;

        // COB
        Double c_cob = 0d;
        if (cobAvailable && cobCheckbox.isChecked()) {
            if (ConfigBuilderPlugin.getActiveAPS().getLastAPSResult() != null && ConfigBuilderPlugin.getActiveAPS().getLastAPSRun().after(new Date(new Date().getTime() - 11 * 60 * 1000L))) {
                try {
                    c_cob = SafeParse.stringToDouble(ConfigBuilderPlugin.getActiveAPS().getLastAPSResult().json().getString("COB"));
                } catch (JSONException e) {
                }
            }
        }

        BolusWizard wizard = new BolusWizard();
        wizard.doCalc(specificProfile, carbsAfterConstraint, c_cob, c_bg, corrAfterConstraint, bolusIobCheckbox.isChecked(), basalIobCheckbox.isChecked(), superbolusCheckbox.isChecked(), bgtrendCheckbox.isChecked());

        bg.setText(c_bg + " ISF: " + DecimalFormatter.to1Decimal(wizard.sens));
        bgInsulin.setText(DecimalFormatter.to2Decimal(wizard.insulinFromBG) + "U");

        carbs.setText(DecimalFormatter.to0Decimal(c_carbs) + "g IC: " + DecimalFormatter.to1Decimal(wizard.ic));
        carbsInsulin.setText(DecimalFormatter.to2Decimal(wizard.insulinFromCarbs) + "U");

        bolusIobInsulin.setText(DecimalFormatter.to2Decimal(wizard.insulingFromBolusIOB) + "U");
        basalIobInsulin.setText(DecimalFormatter.to2Decimal(wizard.insulingFromBasalsIOB) + "U");

        correctionInsulin.setText(DecimalFormatter.to2Decimal(wizard.insulinFromCorrection) + "U");
        calculatedTotalInsulin = wizard.calculatedTotalInsulin;

        if (calculatedTotalInsulin <= 0) {
            total.setText(getString(R.string.missing) + " " + DecimalFormatter.to0Decimal(wizard.carbsEquivalent) + "g");
            totalInsulin.setText("");
        } else {
            total.setText("");
            totalInsulin.setText(DecimalFormatter.to2Decimal(calculatedTotalInsulin) + "U");
        }

        calculatedCarbs = carbsAfterConstraint;

        // Superbolus
        if (superbolusCheckbox.isChecked()) {
            superbolus.setText("2h");
        } else {
            superbolus.setText("");
        }
        superbolusInsulin.setText(DecimalFormatter.to2Decimal(wizard.insulinFromSuperBolus) + "U");

        // Trend
        if (bgtrendCheckbox.isChecked()) {
            if (wizard.glucoseStatus != null) {
                bgTrend.setText((wizard.glucoseStatus.avgdelta > 0 ? "+" : "") + NSProfile.toUnitsString(wizard.glucoseStatus.avgdelta * 3, wizard.glucoseStatus.avgdelta * 3 / 18, profile.getUnits()) + " " + profile.getUnits());
            } else {
                bgTrend.setText("");
            }
        } else {
            bgTrend.setText("");
        }
        bgTrendInsulin.setText(DecimalFormatter.to2Decimal(wizard.insulinFromTrend) + "U");

        // COB
        if (cobAvailable && cobCheckbox.isChecked()) {
            cob.setText(DecimalFormatter.to2Decimal(c_cob) + "g IC: " + DecimalFormatter.to1Decimal(wizard.ic));
            cobInsulin.setText(DecimalFormatter.to2Decimal(wizard.insulinFromCOB) + "U");
        } else {
            cob.setText("");
            cobInsulin.setText("");
        }

        if (calculatedTotalInsulin > 0d || calculatedCarbs > 0d) {
            String insulinText = calculatedTotalInsulin > 0d ? (DecimalFormatter.to2Decimal(calculatedTotalInsulin) + "U") : "";
            String carbsText = calculatedCarbs > 0d ? (DecimalFormatter.to0Decimal(calculatedCarbs) + "g") : "";
            wizardDialogDeliverButton.setText(getString(R.string.send) + " " + insulinText + " " + carbsText);
            wizardDialogDeliverButton.setVisibility(Button.VISIBLE);
        } else {
            wizardDialogDeliverButton.setVisibility(Button.INVISIBLE);
        }

        boluscalcJSON = new JSONObject();
        try {
            boluscalcJSON.put("profile", selectedAlternativeProfile);
            boluscalcJSON.put("eventTime", DateUtil.toISOString(new Date()));
            boluscalcJSON.put("targetBGLow", wizard.targetBGLow);
            boluscalcJSON.put("targetBGHigh", wizard.targetBGHigh);
            boluscalcJSON.put("isf", wizard.sens);
            boluscalcJSON.put("ic", wizard.ic);
            boluscalcJSON.put("iob", -(wizard.insulingFromBolusIOB + wizard.insulingFromBasalsIOB));
            boluscalcJSON.put("bolusiobused", bolusIobCheckbox.isChecked());
            boluscalcJSON.put("basaliobused", basalIobCheckbox.isChecked());
            boluscalcJSON.put("bg", c_bg);
            boluscalcJSON.put("insulinbg", wizard.insulinFromBG);
            boluscalcJSON.put("insulinbgused", bgCheckbox.isChecked());
            boluscalcJSON.put("bgdiff", wizard.bgDiff);
            boluscalcJSON.put("insulincarbs", wizard.insulinFromCarbs);
            boluscalcJSON.put("carbs", c_carbs);
            boluscalcJSON.put("cob", c_cob);
            boluscalcJSON.put("insulincob", wizard.insulinFromCOB);
            boluscalcJSON.put("othercorrection", corrAfterConstraint);
            boluscalcJSON.put("insulinsuperbolus", wizard.insulinFromSuperBolus);
            boluscalcJSON.put("insulintrend", wizard.insulinFromTrend);
            boluscalcJSON.put("insulin", calculatedTotalInsulin);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
