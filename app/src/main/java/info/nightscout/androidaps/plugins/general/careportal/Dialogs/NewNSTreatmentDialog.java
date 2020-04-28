package info.nightscout.androidaps.plugins.general.careportal.Dialogs;


import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;

import com.google.common.collect.Lists;
import com.wdullaer.materialdatetimepicker.date.DatePickerDialog;
import com.wdullaer.materialdatetimepicker.time.TimePickerDialog;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import dagger.android.support.DaggerDialogFragment;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.ProfileStore;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.ProfileSwitch;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunction;
import info.nightscout.androidaps.plugins.general.careportal.OptionsToShow;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.DefaultValueHelper;
import info.nightscout.androidaps.utils.HardLimits;
import info.nightscout.androidaps.utils.JsonHelper;
import info.nightscout.androidaps.utils.NumberPicker;
import info.nightscout.androidaps.utils.alertDialogs.OKDialog;
import info.nightscout.androidaps.utils.SafeParse;
import info.nightscout.androidaps.utils.Translator;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

public class NewNSTreatmentDialog extends DaggerDialogFragment implements View.OnClickListener, DatePickerDialog.OnDateSetListener, TimePickerDialog.OnTimeSetListener {
    @Inject HasAndroidInjector injector;
    @Inject AAPSLogger aapsLogger;
    @Inject DefaultValueHelper defaultValueHelper;
    @Inject ProfileFunction profileFunction;
    @Inject ResourceHelper resourceHelper;
    @Inject ConstraintChecker constraintChecker;
    @Inject SP sp;
    @Inject ActivePluginProvider activePlugin;
    @Inject TreatmentsPlugin treatmentsPlugin;
    @Inject HardLimits hardLimits;
    @Inject Translator translator;

    private static OptionsToShow options;
    private static @StringRes int event;

    private Profile profile;
    public ProfileStore profileStore;

    TextView eventTypeText;
    LinearLayout layoutPercent;
    LinearLayout layoutAbsolute;
    LinearLayout layoutReuse;


    TextView dateButton;
    TextView timeButton;

    TextView bgUnitsView;
    RadioButton meterRadioButton;
    RadioButton sensorRadioButton;
    RadioButton otherRadioButton;
    EditText notesEdit;
    Spinner profileSpinner;
    Spinner reasonSpinner;
    Button reuseButton;

    NumberPicker editBg;
    NumberPicker editCarbs;
    NumberPicker editInsulin;
    NumberPicker editSplit;
    NumberPicker editDuration;
    NumberPicker editPercent;
    NumberPicker editAbsolute;
    NumberPicker editCarbTime;
    NumberPicker editTemptarget;
    NumberPicker editPercentage;
    NumberPicker editTimeshift;

    Date eventTime;

    private static Integer seconds = null;

    public NewNSTreatmentDialog setOptions(OptionsToShow options, int event) {
        this.options = options;
        this.event = event;
        return this;
    }

    public NewNSTreatmentDialog() {
        super();

        if (seconds == null) {
            seconds = Double.valueOf(Math.random() * 59).intValue();
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        if (options == null) return null;
        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        setCancelable(true);
        getDialog().setCanceledOnTouchOutside(false);
        setStyle(DialogFragment.STYLE_NORMAL, getTheme());
        View view = inflater.inflate(R.layout.careportal_newnstreatment_dialog, container, false);

        layoutPercent = view.findViewById(R.id.careportal_newnstreatment_percent_layout);
        layoutAbsolute = view.findViewById(R.id.careportal_newnstreatment_absolute_layout);

        layoutReuse = view.findViewById(R.id.careportal_newnstreatment_reuse_layout);

        eventTypeText = view.findViewById(R.id.careportal_newnstreatment_eventtype);
        eventTypeText.setText(event);
        bgUnitsView = view.findViewById(R.id.careportal_newnstreatment_bgunits);
        meterRadioButton = view.findViewById(R.id.careportal_newnstreatment_meter);
        sensorRadioButton = view.findViewById(R.id.careportal_newnstreatment_sensor);
        otherRadioButton = view.findViewById(R.id.careportal_newnstreatment_other);
        profileSpinner = view.findViewById(R.id.careportal_newnstreatment_profile);

        reuseButton = view.findViewById(R.id.careportal_newnstreatment_reusebutton);

        notesEdit = view.findViewById(R.id.careportal_newnstreatment_notes);

        reasonSpinner = view.findViewById(R.id.careportal_newnstreatment_temptarget_reason);

        eventTime = new Date();
        dateButton = view.findViewById(R.id.careportal_newnstreatment_eventdate);
        timeButton = view.findViewById(R.id.careportal_newnstreatment_eventtime);
        dateButton.setText(DateUtil.dateString(eventTime));
        timeButton.setText(DateUtil.timeString(eventTime));
        dateButton.setOnClickListener(this);
        timeButton.setOnClickListener(this);

        view.findViewById(R.id.ok).setOnClickListener(this);
        view.findViewById(R.id.cancel).setOnClickListener(this);

        // profile
        profile = profileFunction.getProfile();
        profileStore = activePlugin.getActiveProfileInterface().getProfile();
        if (profileStore == null) {
            if (options.eventType == R.id.careportal_profileswitch) {
                aapsLogger.error("Profile switch called but plugin doesn't contain valid profile");
            }
        } else {
            ArrayList<CharSequence> profileList;
            profileList = profileStore.getProfileList();
            ArrayAdapter<CharSequence> adapter = new ArrayAdapter<>(getContext(),
                    R.layout.spinner_centered, profileList);
            profileSpinner.setAdapter(adapter);
            // set selected to actual profile
            for (int p = 0; p < profileList.size(); p++) {
                if (profileList.get(p).equals(profileFunction.getProfileName(false)))
                    profileSpinner.setSelection(p);
            }
        }
        final Double bg = Profile.fromMgdlToUnits(new GlucoseStatus(injector).getGlucoseStatusData() != null ? new GlucoseStatus(injector).getGlucoseStatusData().glucose : 0d, profileFunction.getUnits());

        // temp target
        final List<String> reasonList = Lists.newArrayList(
                resourceHelper.gs(R.string.manual),
                resourceHelper.gs(R.string.eatingsoon),
                resourceHelper.gs(R.string.activity),
                resourceHelper.gs(R.string.hypo));
        ArrayAdapter<String> adapterReason = new ArrayAdapter<>(getContext(),
                R.layout.spinner_centered, reasonList);
        reasonSpinner.setAdapter(adapterReason);
        reasonSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                double defaultDuration;
                double defaultTarget;
                if (profile != null && editTemptarget.getValue().equals(bg)) {
                    defaultTarget = bg;
                } else {
                    //prevent changes on screen rotate
                    defaultTarget = editTemptarget.getValue();
                }
                boolean erase = false;

                if (resourceHelper.gs(R.string.eatingsoon).equals(reasonList.get(position))) {
                    defaultDuration = defaultValueHelper.determineEatingSoonTTDuration();
                    defaultTarget = defaultValueHelper.determineEatingSoonTT();
                } else if (resourceHelper.gs(R.string.activity).equals(reasonList.get(position))) {
                    defaultDuration = defaultValueHelper.determineActivityTTDuration();
                    defaultTarget = defaultValueHelper.determineActivityTT();
                } else if (resourceHelper.gs(R.string.hypo).equals(reasonList.get(position))) {
                    defaultDuration = defaultValueHelper.determineHypoTTDuration();
                    defaultTarget = defaultValueHelper.determineHypoTT();
                } else if (editDuration.getValue() != 0) {
                    defaultDuration = editDuration.getValue();
                } else {
                    defaultDuration = 0;
                    erase = true;
                }

                if (defaultTarget != 0 || erase) {
                    editTemptarget.setValue(defaultTarget);
                }
                if (defaultDuration != 0) {
                    editDuration.setValue(defaultDuration);
                } else if (erase) {
                    editDuration.setValue(0d);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        // bg
        bgUnitsView.setText(profileFunction.getUnits());

        TextWatcher bgTextWatcher = new TextWatcher() {

            public void afterTextChanged(Editable s) {
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (sensorRadioButton.isChecked()) meterRadioButton.setChecked(true);
            }
        };
        editBg = view.findViewById(R.id.careportal_newnstreatment_bginput);
        editTemptarget = view.findViewById(R.id.careportal_newnstreatment_temptarget);
        if (profile == null) {
            editBg.setParams(bg, 0d, 500d, 0.1d, new DecimalFormat("0"), false, view.findViewById(R.id.ok), bgTextWatcher);
            editTemptarget.setParams(Constants.MIN_TT_MGDL, Constants.MIN_TT_MGDL, Constants.MAX_TT_MGDL, 0.1d, new DecimalFormat("0.0"), false, view.findViewById(R.id.ok));
        } else if (profileFunction.getUnits().equals(Constants.MMOL)) {
            editBg.setParams(bg, 0d, 30d, 0.1d, new DecimalFormat("0.0"), false, view.findViewById(R.id.ok), bgTextWatcher);
            editTemptarget.setParams(Constants.MIN_TT_MMOL, Constants.MIN_TT_MMOL, Constants.MAX_TT_MMOL, 0.1d, new DecimalFormat("0.0"), false, view.findViewById(R.id.ok));
        } else {
            editBg.setParams(bg, 0d, 500d, 1d, new DecimalFormat("0"), false, view.findViewById(R.id.ok), bgTextWatcher);
            editTemptarget.setParams(Constants.MIN_TT_MGDL, Constants.MIN_TT_MGDL, Constants.MAX_TT_MGDL, 1d, new DecimalFormat("0"), false, view.findViewById(R.id.ok));
        }

        sensorRadioButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            double bg1 = Profile.fromMgdlToUnits(new GlucoseStatus(injector).getGlucoseStatusData() != null ? new GlucoseStatus(injector).getGlucoseStatusData().glucose : 0d, profileFunction.getUnits());
            if (savedInstanceState != null && savedInstanceState.getDouble("editBg") != bg1) {
                editBg.setValue(savedInstanceState.getDouble("editBg"));
            } else {
                editBg.setValue(bg1);
            }
        });

        Integer maxCarbs = constraintChecker.getMaxCarbsAllowed().value();
        editCarbs = view.findViewById(R.id.careportal_newnstreatment_carbsinput);
        editCarbs.setParams(0d, 0d, (double) maxCarbs, 1d, new DecimalFormat("0"), false, view.findViewById(R.id.ok));

        Double maxInsulin = constraintChecker.getMaxBolusAllowed().value();
        editInsulin = view.findViewById(R.id.careportal_newnstreatment_insulininput);
        editInsulin.setParams(0d, 0d, maxInsulin, 0.05d, new DecimalFormat("0.00"), false, view.findViewById(R.id.ok));

        editSplit = view.findViewById(R.id.careportal_newnstreatment_splitinput);
        editSplit.setParams(100d, 0d, 100d, 5d, new DecimalFormat("0"), true, view.findViewById(R.id.ok));
        editDuration = view.findViewById(R.id.careportal_newnstreatment_durationinput);
        editDuration.setParams(0d, 0d, Constants.MAX_PROFILE_SWITCH_DURATION, 10d, new DecimalFormat("0"), false, view.findViewById(R.id.ok));

        TextWatcher percentTextWatcher = new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start,
                                          int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start,
                                      int before, int count) {
                layoutPercent.setVisibility(View.VISIBLE);
                layoutAbsolute.setVisibility(View.GONE);
            }
        };

        Integer maxPercent = 200;
        if (profile != null)
            maxPercent = constraintChecker.getMaxBasalPercentAllowed(profile).value();
        editPercent = view.findViewById(R.id.careportal_newnstreatment_percentinput);
        editPercent.setParams(0d, -100d, (double) maxPercent, 5d, new DecimalFormat("0"), true, view.findViewById(R.id.ok), percentTextWatcher);

        TextWatcher absoluteTextWatcher = new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start,
                                          int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start,
                                      int before, int count) {
                layoutPercent.setVisibility(View.GONE);
                layoutAbsolute.setVisibility(View.VISIBLE);
            }
        };

        Double maxAbsolute = hardLimits.maxBasal();
        if (profile != null)
            maxAbsolute = constraintChecker.getMaxBasalAllowed(profile).value();
        editAbsolute = view.findViewById(R.id.careportal_newnstreatment_absoluteinput);
        editAbsolute.setParams(0d, 0d, maxAbsolute, 0.05d, new DecimalFormat("0.00"), true, view.findViewById(R.id.ok), absoluteTextWatcher);

        editCarbTime = view.findViewById(R.id.careportal_newnstreatment_carbtimeinput);
        editCarbTime.setParams(0d, -60d, 60d, 5d, new DecimalFormat("0"), false, view.findViewById(R.id.ok));

        editPercentage = view.findViewById(R.id.careportal_newnstreatment_percentage);
        editPercentage.setParams(100d, (double) Constants.CPP_MIN_PERCENTAGE, (double) Constants.CPP_MAX_PERCENTAGE, 1d, new DecimalFormat("0"), false, view.findViewById(R.id.ok));

        editTimeshift = view.findViewById(R.id.careportal_newnstreatment_timeshift);
        editTimeshift.setParams(0d, (double) Constants.CPP_MIN_TIMESHIFT, (double) Constants.CPP_MAX_TIMESHIFT, 1d, new DecimalFormat("0"), false, view.findViewById(R.id.ok));

        ProfileSwitch ps = treatmentsPlugin.getProfileSwitchFromHistory(DateUtil.now());
        if (ps != null && ps.isCPP) {
            final int percentage = ps.percentage;
            final int timeshift = ps.timeshift;
            reuseButton.setText(reuseButton.getText() + " " + percentage + "% " + timeshift + "h");
            reuseButton.setOnClickListener(v -> {
                editPercentage.setValue((double) percentage);
                editTimeshift.setValue((double) timeshift);
            });
        }
        if (ps == null) {
            options.duration = false;
        }

        showOrHide(view.findViewById(R.id.careportal_newnstreatment_eventtime_layout), options.date);
        showOrHide(view.findViewById(R.id.careportal_newnstreatment_bg_layout), options.bg);
        showOrHide(view.findViewById(R.id.careportal_newnstreatment_bgsource_layout), options.bg);
        showOrHide(view.findViewById(R.id.careportal_newnstreatment_insulin_layout), options.insulin);
        showOrHide(view.findViewById(R.id.careportal_newnstreatment_carbs_layout), options.carbs);
        showOrHide(view.findViewById(R.id.careportal_newnstreatment_split_layout), options.split);
        showOrHide(view.findViewById(R.id.careportal_newnstreatment_duration_layout), options.duration);
        showOrHide(layoutPercent, options.percent);
        showOrHide(layoutAbsolute, options.absolute);
        showOrHide(view.findViewById(R.id.careportal_newnstreatment_carbtime_layout), options.prebolus);
        showOrHide(view.findViewById(R.id.careportal_newnstreatment_profile_layout), options.profile);
        showOrHide(view.findViewById(R.id.careportal_newnstreatment_percentage_layout), options.profile);
        showOrHide(view.findViewById(R.id.careportal_newnstreatment_timeshift_layout), options.profile);
        showOrHide(view.findViewById(R.id.careportal_newnstreatment_reuse_layout), options.profile && ps != null && ps.isCPP);
        showOrHide(view.findViewById(R.id.careportal_newnstreatment_temptarget_layout), options.tempTarget);

        setCancelable(true);
        getDialog().setCanceledOnTouchOutside(false);
        //recovering state if there is something
        // only numberPickers and editTexts
        if (savedInstanceState != null) {
            editBg.setValue(savedInstanceState.getDouble("editBg"));
            editTemptarget.setValue(savedInstanceState.getDouble("editTemptarget"));
            notesEdit.setText(savedInstanceState.getString("notesEdit"));
            editCarbs.setValue(savedInstanceState.getDouble("editCarbs"));
            editCarbs.setValue(savedInstanceState.getDouble("editCarbs"));
            editInsulin.setValue(savedInstanceState.getDouble("editInsulin"));
            editDuration.setValue(savedInstanceState.getDouble("editDuration"));
            editPercent.setValue(savedInstanceState.getDouble("editPercent"));
            editAbsolute.setValue(savedInstanceState.getDouble("editAbsolute"));
            editCarbTime.setValue(savedInstanceState.getDouble("editCarbTime"));
            editPercentage.setValue(savedInstanceState.getDouble("editPercentage"));
            editTimeshift.setValue(savedInstanceState.getDouble("editTimeshift"));
            // time and date
            dateButton.setText(savedInstanceState.getString("dateButton"));
            timeButton.setText(savedInstanceState.getString("timeButton"));
        }
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

    }

    @Override
    public void onClick(View view) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(eventTime);
        switch (view.getId()) {
            case R.id.careportal_newnstreatment_eventdate:
                DatePickerDialog dpd = DatePickerDialog.newInstance(
                        this,
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)
                );
                dpd.setThemeDark(true);
                dpd.dismissOnPause(true);
                dpd.show(getActivity().getSupportFragmentManager(), "Datepickerdialog");
                break;
            case R.id.careportal_newnstreatment_eventtime:
                TimePickerDialog tpd = TimePickerDialog.newInstance(
                        this,
                        calendar.get(Calendar.HOUR_OF_DAY),
                        calendar.get(Calendar.MINUTE),
                        DateFormat.is24HourFormat(getContext())
                );
                tpd.setThemeDark(true);
                tpd.dismissOnPause(true);
                tpd.show(getActivity().getSupportFragmentManager(), "Timepickerdialog");
                break;
            case R.id.ok:
                confirmNSTreatmentCreation();
                dismiss();
                break;
            case R.id.cancel:
                dismiss();
                break;
        }
    }

    private void showOrHide(ViewGroup layout, boolean visible) {
        if (visible) layout.setVisibility(View.VISIBLE);
        else layout.setVisibility(View.GONE);
    }

    private void updateBGforDateTime() {
        long millis = eventTime.getTime() - (150 * 1000L); // 2,5 * 60 * 1000
        List<BgReading> data = MainApp.getDbHelper().getBgreadingsDataFromTime(millis, true);
        if ((data.size() > 0) &&
                (data.get(0).date > millis - 7 * 60 * 1000L) &&
                (data.get(0).date < millis + 7 * 60 * 1000L)) {
            editBg.setValue(Profile.fromMgdlToUnits(data.get(0).value, profileFunction.getUnits()));
        }
    }

    @Override
    public void onDateSet(DatePickerDialog view, int year, int monthOfYear, int dayOfMonth) {
        eventTime.setYear(year - 1900);
        eventTime.setMonth(monthOfYear);
        eventTime.setDate(dayOfMonth);
        dateButton.setText(DateUtil.dateString(eventTime));
        updateBGforDateTime();
    }

    @Override
    public void onTimeSet(TimePickerDialog view, int hourOfDay, int minute, int second) {
        eventTime.setHours(hourOfDay);
        eventTime.setMinutes(minute);
        eventTime.setSeconds(this.seconds++); // randomize seconds to prevent creating record of the same time, if user choose time manually
        timeButton.setText(DateUtil.timeString(eventTime));
        updateBGforDateTime();
    }

    private JSONObject gatherData() {
        String enteredBy = sp.getString("careportal_enteredby", "");
        JSONObject data = new JSONObject();
        try {
            boolean allowZeroDuration = false;
            data.put("created_at", DateUtil.toISOString(eventTime));
            switch (options.eventType) {
                case R.id.careportal_bgcheck:
                    data.put("eventType", CareportalEvent.BGCHECK);
                    break;
                case R.id.careportal_announcement:
                    data.put("eventType", CareportalEvent.ANNOUNCEMENT);
                    data.put("isAnnouncement", true);
                    break;
                case R.id.careportal_cgmsensorinsert:
                    data.put("eventType", CareportalEvent.SENSORCHANGE);
                    break;
                case R.id.careportal_cgmsensorstart:
                    data.put("eventType", "Sensor Start");
                    break;
                case R.id.careportal_combobolus:
                    data.put("splitNow", SafeParse.stringToDouble(editSplit.getText()));
                    data.put("splitExt", 100 - SafeParse.stringToDouble(editSplit.getText()));
                    data.put("eventType", CareportalEvent.COMBOBOLUS);
                    break;
                case R.id.careportal_correctionbolus:
                    data.put("eventType", "Correction Bolus");
                    break;
                case R.id.careportal_carbscorrection:
                    data.put("eventType", "Carb Correction");
                    break;
                case R.id.careportal_exercise:
                    data.put("eventType", CareportalEvent.EXERCISE);
                    break;
                case R.id.careportal_insulincartridgechange:
                    data.put("eventType", CareportalEvent.INSULINCHANGE);
                    break;
                case R.id.careportal_pumpbatterychange:
                    data.put("eventType", CareportalEvent.PUMPBATTERYCHANGE);
                    break;
                case R.id.careportal_mealbolus:
                    data.put("eventType", "Meal Bolus");
                    break;
                case R.id.careportal_note:
                    data.put("eventType", CareportalEvent.NOTE);
                    break;
                case R.id.careportal_profileswitch:
                    data.put("eventType", CareportalEvent.PROFILESWITCH);
                    allowZeroDuration = true;
                    break;
                case R.id.careportal_pumpsitechange:
                    data.put("eventType", CareportalEvent.SITECHANGE);
                    break;
                case R.id.careportal_question:
                    data.put("eventType", CareportalEvent.QUESTION);
                    break;
                case R.id.careportal_snackbolus:
                    data.put("eventType", "Snack Bolus");
                    break;
                case R.id.careportal_tempbasalstart:
                    data.put("eventType", CareportalEvent.TEMPBASAL);
                    break;
                case R.id.careportal_tempbasalend:
                    data.put("eventType", CareportalEvent.TEMPBASAL);
                    break;
                case R.id.careportal_openapsoffline:
                    data.put("eventType", CareportalEvent.OPENAPSOFFLINE);
                    break;
                case R.id.careportal_temporarytarget:
                    data.put("eventType", CareportalEvent.TEMPORARYTARGET);
                    if (!reasonSpinner.getSelectedItem().toString().equals(""))
                        data.put("reason", reasonSpinner.getSelectedItem().toString());
                    if (SafeParse.stringToDouble(editTemptarget.getText()) != 0d) {
                        data.put("targetBottom", SafeParse.stringToDouble(editTemptarget.getText()));
                        data.put("targetTop", SafeParse.stringToDouble(editTemptarget.getText()));
                    }
                    allowZeroDuration = true;
                    break;
            }
            if (options.bg && SafeParse.stringToDouble(editBg.getText()) != 0d) {
                data.put("glucose", SafeParse.stringToDouble(editBg.getText()));
                if (meterRadioButton.isChecked()) data.put("glucoseType", "Finger");
                if (sensorRadioButton.isChecked()) data.put("glucoseType", "Sensor");
                if (otherRadioButton.isChecked()) data.put("glucoseType", "Manual");
            }
            if (SafeParse.stringToDouble(editCarbs.getText()) != 0d)
                data.put("carbs", SafeParse.stringToDouble(editCarbs.getText()));
            if (SafeParse.stringToDouble(editInsulin.getText()) != 0d)
                data.put("insulin", SafeParse.stringToDouble(editInsulin.getText()));
            if (allowZeroDuration || SafeParse.stringToDouble(editDuration.getText()) != 0d)
                data.put("duration", SafeParse.stringToDouble(editDuration.getText()));
            if (layoutPercent.getVisibility() != View.GONE)
                data.put("percent", SafeParse.stringToDouble(editPercent.getText()));
            if (layoutAbsolute.getVisibility() != View.GONE)
                data.put("absolute", SafeParse.stringToDouble(editAbsolute.getText()));
            if (options.profile && profileSpinner.getSelectedItem() != null)
                data.put("profile", profileSpinner.getSelectedItem().toString());
            if (options.profile)
                data.put("percentage", SafeParse.stringToInt(editPercentage.getText()));
            if (options.profile)
                data.put("timeshift", SafeParse.stringToInt(editTimeshift.getText()));
            if (SafeParse.stringToDouble(editCarbTime.getText()) != 0d)
                data.put("preBolus", SafeParse.stringToDouble(editCarbTime.getText()));
            if (!notesEdit.getText().toString().equals(""))
                data.put("notes", notesEdit.getText().toString());
            data.put("units", profileFunction.getUnits());
            if (!enteredBy.equals("")) data.put("enteredBy", enteredBy);
            if (options.eventType == R.id.careportal_combobolus) {
                Double enteredInsulin = SafeParse.stringToDouble(editInsulin.getText());
                data.put("enteredinsulin", enteredInsulin);
                data.put("insulin", enteredInsulin * SafeParse.stringToDouble(editSplit.getText()) / 100);
                data.put("relative", enteredInsulin * (100 - SafeParse.stringToDouble(editSplit.getText())) / 100 / SafeParse.stringToDouble(editDuration.getText()) * 60);
            }
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        return data;
    }

    private String buildConfirmText(JSONObject data) {
        String ret = "";
//        if (data.has("eventType")) {
//            ret += resourceHelper.gs(R.string.careportal_newnstreatment_eventtype);
//            ret += ": ";
//            ret += Translator.translate(JsonHelper.safeGetString(data, "eventType", ""));
//            ret += "\n";
//        }
        if (data.has("glucose")) {
            ret += resourceHelper.gs(R.string.treatments_wizard_bg_label);
            ret += ": ";
            ret += JsonHelper.safeGetObject(data, "glucose", "");
            ret += " " + profileFunction.getUnits() + "\n";
        }
        if (data.has("glucoseType")) {
            ret += resourceHelper.gs(R.string.careportal_newnstreatment_glucosetype);
            ret += ": ";
            ret += translator.translate(JsonHelper.safeGetString(data, "glucoseType", ""));
            ret += "\n";
        }
        if (data.has("carbs")) {
            ret += resourceHelper.gs(R.string.careportal_newnstreatment_carbs_label);
            ret += ": ";
            ret += JsonHelper.safeGetObject(data, "carbs", "");
            ret += " g\n";
        }
        if (data.has("insulin")) {
            ret += resourceHelper.gs(R.string.careportal_newnstreatment_insulin_label);
            ret += ": ";
            ret += JsonHelper.safeGetObject(data, "insulin", "");
            ret += " U\n";
        }
        if (data.has("duration")) {
            ret += resourceHelper.gs(R.string.careportal_newnstreatment_duration_label);
            ret += ": ";
            ret += JsonHelper.safeGetObject(data, "duration", "");
            ret += " min\n";
        }
        if (data.has("percent")) {
            ret += resourceHelper.gs(R.string.careportal_newnstreatment_percent_label);
            ret += ": ";
            ret += JsonHelper.safeGetObject(data, "percent", "");
            ret += " %\n";
        }
        if (data.has("absolute")) {
            ret += resourceHelper.gs(R.string.careportal_newnstreatment_absolute_label);
            ret += ": ";
            ret += JsonHelper.safeGetObject(data, "absolute", "");
            ret += " U/h\n";
        }
        if (data.has("preBolus")) {
            ret += resourceHelper.gs(R.string.careportal_newnstreatment_carbtime_label);
            ret += ": ";
            ret += JsonHelper.safeGetObject(data, "preBolus", "");
            ret += " min\n";
        }
        if (data.has("notes")) {
            ret += resourceHelper.gs(R.string.careportal_newnstreatment_notes_label);
            ret += ": ";
            ret += JsonHelper.safeGetObject(data, "notes", "");
            ret += "\n";
        }
        if (data.has("profile")) {
            ret += resourceHelper.gs(R.string.careportal_newnstreatment_profile_label);
            ret += ": ";
            ret += JsonHelper.safeGetObject(data, "profile", "");
            ret += "\n";
        }
        if (data.has("percentage")) {
            ret += resourceHelper.gs(R.string.careportal_newnstreatment_percentage_label);
            ret += ": ";
            ret += JsonHelper.safeGetObject(data, "percentage", "");
            ret += " %\n";
        }
        if (data.has("timeshift")) {
            ret += resourceHelper.gs(R.string.careportal_newnstreatment_timeshift_label);
            ret += ": ";
            ret += JsonHelper.safeGetObject(data, "timeshift", "");
            ret += " h\n";
        }
        if (data.has("targetBottom") && data.has("targetTop")) {
            ret += resourceHelper.gs(R.string.target_range);
            ret += " ";
            ret += JsonHelper.safeGetObject(data, "targetBottom", "");
            ret += " - ";
            ret += JsonHelper.safeGetObject(data, "targetTop", "");
            ret += "\n";
        }
        if (data.has("created_at")) {
            ret += resourceHelper.gs(R.string.event_time_label);
            ret += ": ";
            ret += DateUtil.dateAndTimeString(eventTime);
            ret += "\n";
        }
        if (data.has("enteredBy")) {
            ret += resourceHelper.gs(R.string.careportal_newnstreatment_enteredby_title);
            ret += ": ";
            ret += JsonHelper.safeGetObject(data, "enteredBy", "");
            ret += "\n";
        }

        return ret;
    }

    private void confirmNSTreatmentCreation() {
        final JSONObject data = gatherData();
        OKDialog.showConfirmation(getContext(), translator.translate(JsonHelper.safeGetString(data, "eventType", resourceHelper.gs(R.string.overview_treatment_label))), buildConfirmText(data), () -> NSUpload.createNSTreatment(data, profileStore, profileFunction, eventTime.getTime()));
    }


    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putString("notesEdit", notesEdit.getText().toString());
        savedInstanceState.putString("dateButton", dateButton.getText().toString());
        savedInstanceState.putString("timeButton", timeButton.getText().toString());
        savedInstanceState.putDouble("editBg", editBg.getValue());
        savedInstanceState.putDouble("editCarbs", editCarbs.getValue());
        savedInstanceState.putDouble("editInsulin", editInsulin.getValue());
        savedInstanceState.putDouble("editDuration", editDuration.getValue());
        savedInstanceState.putDouble("editPercent", editPercent.getValue());
        savedInstanceState.putDouble("editAbsolute", editAbsolute.getValue());
        savedInstanceState.putDouble("editCarbTime", editCarbTime.getValue());
        savedInstanceState.putDouble("editTemptarget", editTemptarget.getValue());
        savedInstanceState.putDouble("editPercentage", editPercentage.getValue());
        savedInstanceState.putDouble("editTimeshift", editTimeshift.getValue());
        super.onSaveInstanceState(savedInstanceState);
    }

}
