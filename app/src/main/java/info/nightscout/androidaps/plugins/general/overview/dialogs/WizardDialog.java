package info.nightscout.androidaps.plugins.general.overview.dialogs;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;
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

import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.ArrayList;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.ProfileStore;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.events.EventFeatureRunning;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.CobInfo;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventAutosensCalculationFinished;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.BolusWizard;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.NumberPicker;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.SafeParse;
import info.nightscout.androidaps.utils.StringUtils;
import info.nightscout.androidaps.utils.ToastUtils;

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

    LinearLayout notesLayout;
    EditText notesEdit;

    Integer calculatedCarbs = 0;
    BolusWizard wizard;

    Context context;

    //one shot guards
    private boolean accepted;
    private boolean okClicked;

    public WizardDialog() {
        super();
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
        MainApp.bus().post(new EventFeatureRunning(EventFeatureRunning.Feature.WIZARD));
    }

    @Override
    public void onPause() {
        super.onPause();
        MainApp.bus().unregister(this);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putBoolean("bgCheckbox", bgCheckbox.isChecked());
        savedInstanceState.putBoolean("ttCheckbox", ttCheckbox.isChecked());
        savedInstanceState.putBoolean("bolusIobCheckbox", bolusIobCheckbox.isChecked());
        savedInstanceState.putBoolean("basalIobCheckbox", basalIobCheckbox.isChecked());
        savedInstanceState.putBoolean("bgtrendCheckbox", bgtrendCheckbox.isChecked());
        savedInstanceState.putBoolean("cobCheckbox", cobCheckbox.isChecked());
        savedInstanceState.putDouble("editBg", editBg.getValue());
        savedInstanceState.putDouble("editCarbs", editCarbs.getValue());
        savedInstanceState.putDouble("editCorr", editCorr.getValue());
        savedInstanceState.putDouble("editCarbTime", editCarbTime.getValue());
        super.onSaveInstanceState(savedInstanceState);
    }


    @Subscribe
    public void onStatusEvent(final EventAutosensCalculationFinished e) {
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
        View view = inflater.inflate(R.layout.overview_wizard_dialog, container, false);

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

        notesLayout = view.findViewById(R.id.treatments_wizard_notes_layout);
        notesLayout.setVisibility(SP.getBoolean(R.string.key_show_notes_entry_dialogs, false) ? View.VISIBLE : View.GONE);
        notesEdit = (EditText) view.findViewById(R.id.treatment_wizard_notes);

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

        Integer maxCarbs = MainApp.getConstraintChecker().getMaxCarbsAllowed().value();
        Double maxCorrection = MainApp.getConstraintChecker().getMaxBolusAllowed().value();

        editBg.setParams(0d, 0d, 500d, 0.1d, new DecimalFormat("0.0"), false, textWatcher);
        editCarbs.setParams(0d, 0d, (double) maxCarbs, 1d, new DecimalFormat("0"), false, textWatcher);
        double bolusstep = ConfigBuilderPlugin.getPlugin().getActivePump().getPumpDescription().bolusStep;
        editCorr.setParams(0d, -maxCorrection, maxCorrection, bolusstep, DecimalFormatter.pumpSupportedBolusFormat(), false, textWatcher);
        editCarbTime.setParams(0d, -60d, 60d, 5d, new DecimalFormat("0"), false);
        initDialog();

        setCancelable(true);
        getDialog().setCanceledOnTouchOutside(false);
        //recovering state if there is something
        if (savedInstanceState != null) {
            editCarbs.setValue(savedInstanceState.getDouble("editCarbs"));
            editBg.setValue(savedInstanceState.getDouble("editBg"));
            editCarbTime.setValue(savedInstanceState.getDouble("editCarbTime"));
            editCorr.setValue(savedInstanceState.getDouble("editCorr"));
        }
        return view;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        saveCheckedStates();
        ttCheckbox.setEnabled(bgCheckbox.isChecked() && TreatmentsPlugin.getPlugin().getTempTargetFromHistory() != null);
        calculateInsulin();
    }

    private void saveCheckedStates() {
        SP.putBoolean(MainApp.gs(R.string.key_wizard_include_cob), cobCheckbox.isChecked());
        SP.putBoolean(MainApp.gs(R.string.key_wizard_include_trend_bg), bgtrendCheckbox.isChecked());
    }

    private void loadCheckedStates() {
        bgtrendCheckbox.setChecked(SP.getBoolean(MainApp.gs(R.string.key_wizard_include_trend_bg), false));
        cobCheckbox.setChecked(SP.getBoolean(MainApp.gs(R.string.key_wizard_include_cob), false));
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        calculateInsulin();
        okButton.setVisibility(View.VISIBLE);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        ToastUtils.showToastInUiThread(context, MainApp.gs(R.string.noprofileselected));
        okButton.setVisibility(View.GONE);
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
                wizard.confirmAndExecute(context);
                dismiss();
                break;
            case R.id.cancel:
                dismiss();
                break;
        }
    }

    private void initDialog() {
        Profile profile = ProfileFunctions.getInstance().getProfile();
        ProfileStore profileStore = ConfigBuilderPlugin.getPlugin().getActiveProfileInterface() != null ? ConfigBuilderPlugin.getPlugin().getActiveProfileInterface().getProfile() : null;

        if (profile == null || profileStore == null) {
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.gs(R.string.noprofile));
            dismiss();
            return;
        }

        ArrayList<CharSequence> profileList;
        profileList = profileStore.getProfileList();
        profileList.add(0, MainApp.gs(R.string.active));
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<>(getContext(),
                R.layout.spinner_centered, profileList);

        profileSpinner.setAdapter(adapter);

        String units = profile.getUnits();
        bgUnits.setText(units);
        if (units.equals(Constants.MGDL)) editBg.setStep(1d);
        else editBg.setStep(0.1d);

        // Set BG if not old
        BgReading lastBg = DatabaseHelper.actualBg();

        if (lastBg != null) {
            editBg.setValue(lastBg.valueToUnits(units));
        } else {
            editBg.setValue(0d);
        }
        ttCheckbox.setEnabled(TreatmentsPlugin.getPlugin().getTempTargetFromHistory() != null);

        // IOB calculation
        TreatmentsPlugin.getPlugin().updateTotalIOBTreatments();
        IobTotal bolusIob = TreatmentsPlugin.getPlugin().getLastCalculationTreatments().round();
        TreatmentsPlugin.getPlugin().updateTotalIOBTempBasals();
        IobTotal basalIob = TreatmentsPlugin.getPlugin().getLastCalculationTempBasals().round();

        bolusIobInsulin.setText(StringUtils.formatInsulin(-bolusIob.iob));
        basalIobInsulin.setText(StringUtils.formatInsulin(-basalIob.basaliob));

        calculateInsulin();
    }

    private void calculateInsulin() {
        ProfileStore profileStore = ConfigBuilderPlugin.getPlugin().getActiveProfileInterface().getProfile();
        if (profileSpinner == null || profileSpinner.getSelectedItem() == null || profileStore == null)
            return; // not initialized yet
        String profileName = profileSpinner.getSelectedItem().toString();
        Profile specificProfile;
        if (profileName.equals(MainApp.gs(R.string.active))) {
            specificProfile = ProfileFunctions.getInstance().getProfile();
            profileName = ProfileFunctions.getInstance().getProfileName();
        } else
            specificProfile = profileStore.getSpecificProfile(profileName);

        // Entered values
        Double c_bg = SafeParse.stringToDouble(editBg.getText());
        Integer c_carbs = SafeParse.stringToInt(editCarbs.getText());
        Double c_correction = SafeParse.stringToDouble(editCorr.getText());
        Double corrAfterConstraint = c_correction;
        if (c_correction > 0)
            c_correction = MainApp.getConstraintChecker().applyBolusConstraints(new Constraint<>(c_correction)).value();
        if (Math.abs(c_correction - corrAfterConstraint) > 0.01d) { // c_correction != corrAfterConstraint doesn't work
            editCorr.setValue(0d);
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.gs(R.string.bolusconstraintapplied));
            return;
        }
        Integer carbsAfterConstraint = MainApp.getConstraintChecker().applyCarbsConstraints(new Constraint<>(c_carbs)).value();
        if (Math.abs(c_carbs - carbsAfterConstraint) > 0.01d) {
            editCarbs.setValue(0d);
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.gs(R.string.carbsconstraintapplied));
            return;
        }

        c_bg = bgCheckbox.isChecked() ? c_bg : 0d;
        TempTarget tempTarget = ttCheckbox.isChecked() ? TreatmentsPlugin.getPlugin().getTempTargetFromHistory() : null;

        // COB
        Double c_cob = 0d;
        if (cobCheckbox.isChecked()) {
            CobInfo cobInfo = IobCobCalculatorPlugin.getPlugin().getCobInfo(false, "Wizard COB");
            if (cobInfo.displayCob != null)
                c_cob = cobInfo.displayCob;
        }

        wizard = new BolusWizard(specificProfile, profileName, tempTarget, carbsAfterConstraint, c_cob, c_bg, corrAfterConstraint, 100d, bgCheckbox.isChecked(), cobCheckbox.isChecked(), bolusIobCheckbox.isChecked(), basalIobCheckbox.isChecked(), superbolusCheckbox.isChecked(), ttCheckbox.isChecked(), bgtrendCheckbox.isChecked(), notesEdit.getText().toString(), SafeParse.stringToInt(editCarbTime.getText()));

        bg.setText(c_bg + " ISF: " + DecimalFormatter.to1Decimal(wizard.getSens()));
        bgInsulin.setText(StringUtils.formatInsulin(wizard.getInsulinFromBG()));

        carbs.setText(DecimalFormatter.to0Decimal(c_carbs) + "g IC: " + DecimalFormatter.to1Decimal(wizard.getIc()));
        carbsInsulin.setText(StringUtils.formatInsulin(wizard.getInsulinFromCarbs()));

        bolusIobInsulin.setText(StringUtils.formatInsulin(wizard.getInsulinFromBolusIOB()));
        basalIobInsulin.setText(StringUtils.formatInsulin(wizard.getInsulinFromBasalsIOB()));

        correctionInsulin.setText(StringUtils.formatInsulin(wizard.getInsulinFromCorrection()));

        calculatedCarbs = carbsAfterConstraint;

        // Superbolus
        superbolus.setText(superbolusCheckbox.isChecked() ? MainApp.gs(R.string.twohours) : "");
        superbolusInsulin.setText(StringUtils.formatInsulin(wizard.getInsulinFromSuperBolus()));

        // Trend
        if (bgtrendCheckbox.isChecked() && wizard.getGlucoseStatus() != null) {
            bgTrend.setText(
                    (wizard.getTrend() > 0 ? "+" : "")
                            + Profile.toUnitsString(wizard.getTrend() * 3, wizard.getTrend() * 3 / Constants.MMOLL_TO_MGDL, specificProfile.getUnits())
                            + " " + specificProfile.getUnits());
        } else {
            bgTrend.setText("");
        }
        bgTrendInsulin.setText(StringUtils.formatInsulin(wizard.getInsulinFromTrend()));

        // COB
        if (cobCheckbox.isChecked()) {
            cob.setText(DecimalFormatter.to2Decimal(c_cob) + "g IC: " + DecimalFormatter.to1Decimal(wizard.getIc()));
            cobInsulin.setText(StringUtils.formatInsulin(wizard.getInsulinFromCOB()));
        } else {
            cob.setText("");
            cobInsulin.setText("");
        }

        if (wizard.getCalculatedTotalInsulin() > 0d || calculatedCarbs > 0d) {
            String insulinText = wizard.getCalculatedTotalInsulin() > 0d ? (DecimalFormatter.toPumpSupportedBolus(wizard.getCalculatedTotalInsulin()) + "U") : "";
            String carbsText = calculatedCarbs > 0d ? (DecimalFormatter.to0Decimal(calculatedCarbs) + "g") : "";
            total.setText(MainApp.gs(R.string.result) + ": " + insulinText + " " + carbsText);
            okButton.setVisibility(View.VISIBLE);
        } else {
            // TODO this should also be run when loading the dialog as the OK button is initially visible
            //      but does nothing if neither carbs nor insulin is > 0
            total.setText(MainApp.gs(R.string.missing) + " " + DecimalFormatter.to0Decimal(wizard.getCarbsEquivalent()) + "g");
            okButton.setVisibility(View.INVISIBLE);
        }

    }

}
