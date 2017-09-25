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
import android.text.Html;
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

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.ProfileStore;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.events.EventNewBG;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Loop.LoopPlugin;
import info.nightscout.androidaps.plugins.OpenAPSAMA.OpenAPSAMAPlugin;
import info.nightscout.androidaps.plugins.OpenAPSMA.events.EventOpenAPSUpdateGui;
import info.nightscout.androidaps.plugins.Overview.Notification;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.utils.BolusWizard;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.NumberPicker;
import info.nightscout.utils.OKDialog;
import info.nightscout.utils.SP;
import info.nightscout.utils.SafeParse;
import info.nightscout.utils.ToastUtils;

public class WizardDialog extends DialogFragment implements OnClickListener, CompoundButton.OnCheckedChangeListener, Spinner.OnItemSelectedListener {
    private static Logger log = LoggerFactory.getLogger(WizardDialog.class);

    Button okButton;
    TextView bg;
    TextView bgInsulin;
    TextView bgUnits;
    CheckBox bgCheckbox;
    CheckBox ttCheckbox;
    TextView carbs;
    TextView carbsInsulin;
    TextView bolusIobInsulin;
    TextView basalIobInsulin;
    CheckBox bolusIobCheckbox;
    CheckBox basalIobCheckbox;
    TextView correctionInsulin;
    TextView total;
    Spinner profileSpinner;
    CheckBox superbolusCheckbox;
    TextView superbolus;
    TextView superbolusInsulin;
    CheckBox bgtrendCheckbox;
    TextView bgTrend;
    TextView bgTrendInsulin;
    LinearLayout cobLayout;
    CheckBox cobCheckbox;
    TextView cob;
    TextView cobInsulin;

    NumberPicker editBg;
    NumberPicker editCarbs;
    NumberPicker editCorr;
    NumberPicker editCarbTime;

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
    public void onDetach() {
        super.onDetach();
        this.context = null;
    }

    @Override
    public void onResume() {
        super.onResume();
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
                    if (ConfigBuilderPlugin.getActiveAPS() instanceof OpenAPSAMAPlugin && ConfigBuilderPlugin.getActiveAPS().getLastAPSResult() != null && ConfigBuilderPlugin.getActiveAPS().getLastAPSRun().after(new Date(System.currentTimeMillis() - 11 * 60 * 1000L))) {
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

        okButton = (Button) view.findViewById(R.id.ok);
        okButton.setOnClickListener(this);
        view.findViewById(R.id.cancel).setOnClickListener(this);

        bg = (TextView) view.findViewById(R.id.treatments_wizard_bg);
        bgInsulin = (TextView) view.findViewById(R.id.treatments_wizard_bginsulin);
        bgUnits = (TextView) view.findViewById(R.id.treatments_wizard_bgunits);
        carbs = (TextView) view.findViewById(R.id.treatments_wizard_carbs);
        carbsInsulin = (TextView) view.findViewById(R.id.treatments_wizard_carbsinsulin);
        bolusIobInsulin = (TextView) view.findViewById(R.id.treatments_wizard_bolusiobinsulin);
        basalIobInsulin = (TextView) view.findViewById(R.id.treatments_wizard_basaliobinsulin);
        correctionInsulin = (TextView) view.findViewById(R.id.treatments_wizard_correctioninsulin);
        total = (TextView) view.findViewById(R.id.treatments_wizard_total);
        superbolus = (TextView) view.findViewById(R.id.treatments_wizard_sb);
        superbolusInsulin = (TextView) view.findViewById(R.id.treatments_wizard_sbinsulin);

        bgTrend = (TextView) view.findViewById(R.id.treatments_wizard_bgtrend);
        bgTrendInsulin = (TextView) view.findViewById(R.id.treatments_wizard_bgtrendinsulin);
        cobLayout = (LinearLayout) view.findViewById(R.id.treatments_wizard_cob_layout);
        cob = (TextView) view.findViewById(R.id.treatments_wizard_cob);
        cobInsulin = (TextView) view.findViewById(R.id.treatments_wizard_cobinsulin);

        bgCheckbox = (CheckBox) view.findViewById(R.id.treatments_wizard_bgcheckbox);
        ttCheckbox = (CheckBox) view.findViewById(R.id.treatments_wizard_ttcheckbox);
        bgtrendCheckbox = (CheckBox) view.findViewById(R.id.treatments_wizard_bgtrendcheckbox);
        cobCheckbox = (CheckBox) view.findViewById(R.id.treatments_wizard_cobcheckbox);
        bolusIobCheckbox = (CheckBox) view.findViewById(R.id.treatments_wizard_bolusiobcheckbox);
        basalIobCheckbox = (CheckBox) view.findViewById(R.id.treatments_wizard_basaliobcheckbox);
        superbolusCheckbox = (CheckBox) view.findViewById(R.id.treatments_wizard_sbcheckbox);
        loadCheckedStates();

        bgCheckbox.setOnCheckedChangeListener(this);
        ttCheckbox.setOnCheckedChangeListener(this);
        bgtrendCheckbox.setOnCheckedChangeListener(this);
        cobCheckbox.setOnCheckedChangeListener(this);
        basalIobCheckbox.setOnCheckedChangeListener(this);
        bolusIobCheckbox.setOnCheckedChangeListener(this);
        superbolusCheckbox.setOnCheckedChangeListener(this);

        profileSpinner = (Spinner) view.findViewById(R.id.treatments_wizard_profile);
        profileSpinner.setOnItemSelectedListener(this);

        editCarbTime = (NumberPicker) view.findViewById(R.id.treatments_wizard_carbtimeinput);
        editCorr = (NumberPicker) view.findViewById(R.id.treatments_wizard_correctioninput);
        editCarbs = (NumberPicker) view.findViewById(R.id.treatments_wizard_carbsinput);
        editBg = (NumberPicker) view.findViewById(R.id.treatments_wizard_bginput);

        superbolusCheckbox.setVisibility(SP.getBoolean(R.string.key_usesuperbolus, false) ? View.VISIBLE : View.GONE);

        Integer maxCarbs = MainApp.getConfigBuilder().applyCarbsConstraints(Constants.carbsOnlyForCheckLimit);
        Double maxCorrection = MainApp.getConfigBuilder().applyBolusConstraints(Constants.bolusOnlyForCheckLimit);

        editBg.setParams(0d, 0d, 500d, 0.1d, new DecimalFormat("0.0"), false, textWatcher);
        editCarbs.setParams(0d, 0d, (double) maxCarbs, 1d, new DecimalFormat("0"), false, textWatcher);
        double bolusstep = MainApp.getConfigBuilder().getPumpDescription().bolusStep;
        editCorr.setParams(0d, -maxCorrection, maxCorrection, bolusstep, new DecimalFormat("0.00"), false, textWatcher);
        editCarbTime.setParams(0d, -60d, 60d, 5d, new DecimalFormat("0"), false);
        initDialog();

        return view;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        saveCheckedStates();
        ttCheckbox.setEnabled(bgCheckbox.isChecked() && MainApp.getConfigBuilder().getTempTargetFromHistory() != null);
        calculateInsulin();
    }

    private void saveCheckedStates() {
        //SP.putBoolean(getString(R.string.key_wizard_include_bg), bgCheckbox.isChecked());
        SP.putBoolean(getString(R.string.key_wizard_include_cob), cobCheckbox.isChecked());
        SP.putBoolean(getString(R.string.key_wizard_include_trend_bg), bgtrendCheckbox.isChecked());
        //SP.putBoolean(getString(R.string.key_wizard_include_bolus_iob), bolusIobCheckbox.isChecked());
        //SP.putBoolean(getString(R.string.key_wizard_include_basal_iob), basalIobCheckbox.isChecked());
    }

    private void loadCheckedStates() {
        //bgCheckbox.setChecked(SP.getBoolean(getString(R.string.key_wizard_include_bg), true));
        bgtrendCheckbox.setChecked(SP.getBoolean(getString(R.string.key_wizard_include_trend_bg), false));
        cobCheckbox.setChecked(SP.getBoolean(getString(R.string.key_wizard_include_cob), false));
        //bolusIobCheckbox.setChecked(SP.getBoolean(getString(R.string.key_wizard_include_bolus_iob), true));
        //basalIobCheckbox.setChecked(SP.getBoolean(getString(R.string.key_wizard_include_basal_iob), true));
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        calculateInsulin();
        okButton.setVisibility(View.VISIBLE);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        ToastUtils.showToastInUiThread(context, MainApp.sResources.getString(R.string.noprofileselected));
        okButton.setVisibility(View.GONE);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.ok:
                if (calculatedTotalInsulin > 0d || calculatedCarbs > 0d) {
                    DecimalFormat formatNumber2decimalplaces = new DecimalFormat("0.00");

                    String confirmMessage = getString(R.string.entertreatmentquestion);

                    Double insulinAfterConstraints = MainApp.getConfigBuilder().applyBolusConstraints(calculatedTotalInsulin);
                    Integer carbsAfterConstraints = MainApp.getConfigBuilder().applyCarbsConstraints(calculatedCarbs);

                    confirmMessage += "<br/>" + getString(R.string.bolus) + ": " + "<font color='" + MainApp.sResources.getColor(R.color.bolus) + "'>" + formatNumber2decimalplaces.format(insulinAfterConstraints) + "U" + "</font>";
                    confirmMessage += "<br/>" + getString(R.string.carbs) + ": " + carbsAfterConstraints + "g";


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
                    final Double bg = SafeParse.stringToDouble(editBg.getText());
                    final int carbTime = SafeParse.stringToInt(editCarbTime.getText());
                    final boolean useSuperBolus = superbolusCheckbox.isChecked();

                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setTitle(MainApp.sResources.getString(R.string.confirmation));
                    builder.setMessage(Html.fromHtml(confirmMessage));
                    builder.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            if (finalInsulinAfterConstraints > 0 || finalCarbsAfterConstraints > 0) {
                                final ConfigBuilderPlugin pump = MainApp.getConfigBuilder();
                                mHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        PumpEnactResult result;
                                        if (useSuperBolus) {
                                            final LoopPlugin activeloop = MainApp.getConfigBuilder().getActiveLoop();
                                            if (activeloop != null) {
                                                activeloop.superBolusTo(System.currentTimeMillis() + 2 * 60L * 60 * 1000);
                                                MainApp.bus().post(new EventRefreshOverview("WizardDialog"));
                                            }
                                            pump.cancelTempBasal(true);
                                            result = pump.setTempBasalAbsolute(0d, 120);
                                            if (!result.success) {
                                                OKDialog.show(getActivity(), MainApp.sResources.getString(R.string.tempbasaldeliveryerror), result.comment, null);
                                            }
                                        }
                                        DetailedBolusInfo detailedBolusInfo = new DetailedBolusInfo();
                                        detailedBolusInfo.eventType = CareportalEvent.BOLUSWIZARD;
                                        detailedBolusInfo.insulin = finalInsulinAfterConstraints;
                                        detailedBolusInfo.carbs = finalCarbsAfterConstraints;
                                        detailedBolusInfo.context = context;
                                        detailedBolusInfo.glucose = bg;
                                        detailedBolusInfo.glucoseType = "Manual";
                                        detailedBolusInfo.carbTime = carbTime;
                                        detailedBolusInfo.boluscalc = boluscalcJSON;
                                        detailedBolusInfo.source = Source.USER;
                                        result = pump.deliverTreatment(detailedBolusInfo);
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
                                Answers.getInstance().logCustom(new CustomEvent("Wizard"));
                            }
                        }
                    });
                    builder.setNegativeButton(getString(R.string.cancel), null);
                    builder.show();
                    dismiss();
                }
                break;
            case R.id.cancel:
                dismiss();
                break;
        }
    }

    private void initDialog() {
        Profile profile = MainApp.getConfigBuilder().getProfile();
        ProfileStore profileStore = MainApp.getConfigBuilder().getActiveProfileInterface().getProfile();

        if (profile == null) {
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.noprofile));
            return;
        }

        ArrayList<CharSequence> profileList;
        profileList = profileStore.getProfileList();
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(getContext(),
                R.layout.spinner_centered, profileList);

        profileSpinner.setAdapter(adapter);
        // set selected to actual profile
        for (int p = 0; p < profileList.size(); p++) {
            if (profileList.get(p).equals(MainApp.getConfigBuilder().getProfileName()))
                profileSpinner.setSelection(p);
        }

        String units = profile.getUnits();
        bgUnits.setText(units);
        if (units.equals(Constants.MGDL)) editBg.setStep(1d);
        else editBg.setStep(0.1d);

        // Set BG if not old
        BgReading lastBg = DatabaseHelper.actualBg();

        if (lastBg != null) {
            editBg.removeTextChangedListener(textWatcher);
            //bgInput.setText(lastBg.valueToUnitsToString(units));
            editBg.setValue(lastBg.valueToUnits(units));
            editBg.addTextChangedListener(textWatcher);
        } else {
            editBg.removeTextChangedListener(textWatcher);
            //bgInput.setText("");
            editBg.setValue(0d);
            editBg.addTextChangedListener(textWatcher);
        }
        ttCheckbox.setEnabled(MainApp.getConfigBuilder().getTempTargetFromHistory() != null);

        // IOB calculation
        MainApp.getConfigBuilder().updateTotalIOBTreatments();
        IobTotal bolusIob = MainApp.getConfigBuilder().getLastCalculationTreatments().round();
        MainApp.getConfigBuilder().updateTotalIOBTempBasals();
        IobTotal basalIob = MainApp.getConfigBuilder().getLastCalculationTempBasals().round();

        bolusIobInsulin.setText(DecimalFormatter.to2Decimal(-bolusIob.iob) + "U");
        basalIobInsulin.setText(DecimalFormatter.to2Decimal(-basalIob.basaliob) + "U");

        // COB only if AMA is selected
        if (ConfigBuilderPlugin.getActiveAPS() instanceof OpenAPSAMAPlugin && ConfigBuilderPlugin.getActiveAPS().getLastAPSResult() != null && ConfigBuilderPlugin.getActiveAPS().getLastAPSRun().after(new Date(System.currentTimeMillis() - 11 * 60 * 1000L))) {
            cobLayout.setVisibility(View.VISIBLE);
            cobAvailable = true;
        } else {
            cobLayout.setVisibility(View.GONE);
            cobAvailable = false;
        }
        calculateInsulin();
    }

    private void calculateInsulin() {
        ProfileStore profile = MainApp.getConfigBuilder().getActiveProfileInterface().getProfile();
        if (profileSpinner == null || profileSpinner.getSelectedItem() == null)
            return; // not initialized yet
        String selectedAlternativeProfile = profileSpinner.getSelectedItem().toString();
        Profile specificProfile = profile.getSpecificProfile(selectedAlternativeProfile);

        // Entered values
        Double c_bg = SafeParse.stringToDouble(editBg.getText());
        Integer c_carbs = SafeParse.stringToInt(editCarbs.getText());
        Double c_correction = SafeParse.stringToDouble(editCorr.getText());
        Double corrAfterConstraint = MainApp.getConfigBuilder().applyBolusConstraints(c_correction);
        if (c_correction - corrAfterConstraint != 0) { // c_correction != corrAfterConstraint doesn't work
            editCorr.removeTextChangedListener(textWatcher);
            editCorr.setValue(0d);
            editCorr.addTextChangedListener(textWatcher);
            //wizardDialogDeliverButton.setVisibility(Button.GONE);
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), getString(R.string.bolusconstraintapplied));
            return;
        }
        Integer carbsAfterConstraint = MainApp.getConfigBuilder().applyCarbsConstraints(c_carbs);
        if (c_carbs - carbsAfterConstraint != 0) {
            editCarbs.removeTextChangedListener(textWatcher);
            editCarbs.setValue(0d);
            editCarbs.addTextChangedListener(textWatcher);
            //wizardDialogDeliverButton.setVisibility(Button.GONE);
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), getString(R.string.carbsconstraintapplied));
            return;
        }

        c_bg = bgCheckbox.isChecked() ? c_bg : 0d;
        TempTarget tempTarget = ttCheckbox.isChecked() ? MainApp.getConfigBuilder().getTempTargetFromHistory() : null;

        // COB
        Double c_cob = 0d;
        if (cobAvailable && cobCheckbox.isChecked()) {
            if (ConfigBuilderPlugin.getActiveAPS().getLastAPSResult() != null && ConfigBuilderPlugin.getActiveAPS().getLastAPSRun().after(new Date(System.currentTimeMillis() - 11 * 60 * 1000L))) {
                try {
                    c_cob = SafeParse.stringToDouble(ConfigBuilderPlugin.getActiveAPS().getLastAPSResult().json().getString("COB"));
                } catch (JSONException e) {
                    log.error("Unhandled exception", e);
                }
            }
        }

        BolusWizard wizard = new BolusWizard();
        wizard.doCalc(specificProfile, tempTarget, carbsAfterConstraint, c_cob, c_bg, corrAfterConstraint, bolusIobCheckbox.isChecked(), basalIobCheckbox.isChecked(), superbolusCheckbox.isChecked(), bgtrendCheckbox.isChecked());

        bg.setText(c_bg + " ISF: " + DecimalFormatter.to1Decimal(wizard.sens));
        bgInsulin.setText(DecimalFormatter.to2Decimal(wizard.insulinFromBG) + "U");

        carbs.setText(DecimalFormatter.to0Decimal(c_carbs) + "g IC: " + DecimalFormatter.to1Decimal(wizard.ic));
        carbsInsulin.setText(DecimalFormatter.to2Decimal(wizard.insulinFromCarbs) + "U");

        bolusIobInsulin.setText(DecimalFormatter.to2Decimal(wizard.insulingFromBolusIOB) + "U");
        basalIobInsulin.setText(DecimalFormatter.to2Decimal(wizard.insulingFromBasalsIOB) + "U");

        correctionInsulin.setText(DecimalFormatter.to2Decimal(wizard.insulinFromCorrection) + "U");
        calculatedTotalInsulin = wizard.calculatedTotalInsulin;

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
                bgTrend.setText((wizard.glucoseStatus.avgdelta > 0 ? "+" : "") + Profile.toUnitsString(wizard.glucoseStatus.avgdelta * 3, wizard.glucoseStatus.avgdelta * 3 / 18, specificProfile.getUnits()) + " " + specificProfile.getUnits());
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
            total.setText(getString(R.string.result) + ": " + insulinText + " " + carbsText);
            okButton.setVisibility(View.VISIBLE);
        } else {
            // TODO this should also be run when loading the dialog as the OK button is initially visible
            //      but does nothing if neither carbs nor insulin is > 0
            total.setText(getString(R.string.missing) + " " + DecimalFormatter.to0Decimal(wizard.carbsEquivalent) + "g");
            okButton.setVisibility(View.INVISIBLE);
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
            log.error("Unhandled exception", e);
        }
    }

}
