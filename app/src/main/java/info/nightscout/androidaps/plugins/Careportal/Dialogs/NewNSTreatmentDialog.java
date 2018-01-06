package info.nightscout.androidaps.plugins.Careportal.Dialogs;


import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;

import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.wdullaer.materialdatetimepicker.date.DatePickerDialog;
import com.wdullaer.materialdatetimepicker.time.RadialPickerLayout;
import com.wdullaer.materialdatetimepicker.time.TimePickerDialog;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.GlucoseStatus;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.ProfileStore;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.ProfileSwitch;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.events.EventNewBasalProfile;
import info.nightscout.androidaps.plugins.Careportal.OptionsToShow;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Overview.Dialogs.ErrorHelperActivity;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.NSUpload;
import info.nightscout.utils.NumberPicker;
import info.nightscout.utils.SP;
import info.nightscout.utils.SafeParse;
import info.nightscout.utils.Translator;

public class NewNSTreatmentDialog extends DialogFragment implements View.OnClickListener, DatePickerDialog.OnDateSetListener, TimePickerDialog.OnTimeSetListener {
    private static Logger log = LoggerFactory.getLogger(NewNSTreatmentDialog.class);

    private Activity context;

    private static OptionsToShow options;
    private static String event;

    Profile profile;
    ProfileStore profileStore;
    String units;

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

    public void setOptions(OptionsToShow options, int event) {
        this.options = options;
        this.event = MainApp.sResources.getString(event);
    }

    public NewNSTreatmentDialog() {
        super();
    }

    @Override
    public void onAttach(Activity activity) {
        context = activity;
        super.onAttach(activity);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        this.context = null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        if (options == null) return null;
        getDialog().setTitle(getString(options.eventName));
        setStyle(DialogFragment.STYLE_NORMAL, getTheme());
        View view = inflater.inflate(R.layout.careportal_newnstreatment_dialog, container, false);

        layoutPercent = (LinearLayout) view.findViewById(R.id.careportal_newnstreatment_percent_layout);
        layoutAbsolute = (LinearLayout) view.findViewById(R.id.careportal_newnstreatment_absolute_layout);

        layoutReuse = (LinearLayout) view.findViewById(R.id.careportal_newnstreatment_reuse_layout);

        eventTypeText = (TextView) view.findViewById(R.id.careportal_newnstreatment_eventtype);
        eventTypeText.setText(event);
        bgUnitsView = (TextView) view.findViewById(R.id.careportal_newnstreatment_bgunits);
        meterRadioButton = (RadioButton) view.findViewById(R.id.careportal_newnstreatment_meter);
        sensorRadioButton = (RadioButton) view.findViewById(R.id.careportal_newnstreatment_sensor);
        otherRadioButton = (RadioButton) view.findViewById(R.id.careportal_newnstreatment_other);
        profileSpinner = (Spinner) view.findViewById(R.id.careportal_newnstreatment_profile);

        reuseButton = (Button) view.findViewById(R.id.careportal_newnstreatment_reusebutton);

        notesEdit = (EditText) view.findViewById(R.id.careportal_newnstreatment_notes);

        reasonSpinner = (Spinner) view.findViewById(R.id.careportal_newnstreatment_temptarget_reason);

        eventTime = new Date();
        dateButton = (TextView) view.findViewById(R.id.careportal_newnstreatment_eventdate);
        timeButton = (TextView) view.findViewById(R.id.careportal_newnstreatment_eventtime);
        dateButton.setText(DateUtil.dateString(eventTime));
        timeButton.setText(DateUtil.timeString(eventTime));
        dateButton.setOnClickListener(this);
        timeButton.setOnClickListener(this);

        view.findViewById(R.id.ok).setOnClickListener(this);
        view.findViewById(R.id.cancel).setOnClickListener(this);

        // profile
        profile = MainApp.getConfigBuilder().getProfile();
        profileStore = ConfigBuilderPlugin.getActiveProfileInterface().getProfile();
        ArrayList<CharSequence> profileList;
        units = profile != null ? profile.getUnits() : Constants.MGDL;
        profileList = profileStore.getProfileList();
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(getContext(),
                R.layout.spinner_centered, profileList);
        profileSpinner.setAdapter(adapter);
        // set selected to actual profile
        for (int p = 0; p < profileList.size(); p++) {
            if (profileList.get(p).equals(MainApp.getConfigBuilder().getProfileName(false)))
                profileSpinner.setSelection(p);
        }

        final Double bg = Profile.fromMgdlToUnits(GlucoseStatus.getGlucoseStatusData() != null ? GlucoseStatus.getGlucoseStatusData().glucose : 0d, profile != null ? profile.getUnits() : Constants.MGDL);

        // temp target
        final ArrayList<CharSequence> reasonList = new ArrayList<CharSequence>();
        reasonList.add(MainApp.sResources.getString(R.string.manual));
        reasonList.add(MainApp.sResources.getString(R.string.eatingsoon));
        reasonList.add(MainApp.sResources.getString(R.string.activity));
        ArrayAdapter<CharSequence> adapterReason = new ArrayAdapter<CharSequence>(getContext(),
                R.layout.spinner_centered, reasonList);
        reasonSpinner.setAdapter(adapterReason);
        reasonSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                double defaultDuration = 0;
                double defaultTarget = 0;
                if (profile != null) {
                    defaultTarget = bg.doubleValue();
                }
                boolean erase = false;

                if (MainApp.sResources.getString(R.string.eatingsoon).equals(reasonList.get(position))) {
                    defaultDuration = SP.getDouble(R.string.key_eatingsoon_duration, 0d);
                    defaultTarget = SP.getDouble(R.string.key_eatingsoon_target, 0d);
                    ;
                } else if (MainApp.sResources.getString(R.string.activity).equals(reasonList.get(position))) {
                    defaultDuration = SP.getDouble(R.string.key_activity_duration, 0d);
                    ;
                    defaultTarget = SP.getDouble(R.string.key_activity_target, 0d);
                    ;
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
        bgUnitsView.setText(units);

        TextWatcher bgTextWatcher = new TextWatcher() {

            public void afterTextChanged(Editable s) {
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (sensorRadioButton.isChecked()) meterRadioButton.setChecked(true);
            }
        };

        editBg = (NumberPicker) view.findViewById(R.id.careportal_newnstreatment_bginput);
        editTemptarget = (NumberPicker) view.findViewById(R.id.careportal_newnstreatment_temptarget);
        if (profile == null) {
            editBg.setParams(bg, 0d, 500d, 0.1d, new DecimalFormat("0.0"), false, bgTextWatcher);
            editTemptarget.setParams(bg, 0d, 500d, 0.1d, new DecimalFormat("0.0"), false);
        } else if (profile.getUnits().equals(Constants.MMOL)) {
            editBg.setParams(bg, 0d, 30d, 0.1d, new DecimalFormat("0.0"), false, bgTextWatcher);
            editTemptarget.setParams(bg, 0d, 30d, 0.1d, new DecimalFormat("0.0"), false);
        } else {
            editBg.setParams(bg, 0d, 500d, 1d, new DecimalFormat("0"), false, bgTextWatcher);
            editTemptarget.setParams(bg, 0d, 500d, 1d, new DecimalFormat("0"), false);
        }
        sensorRadioButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Double bg = Profile.fromMgdlToUnits(GlucoseStatus.getGlucoseStatusData() != null ? GlucoseStatus.getGlucoseStatusData().glucose : 0d, profile.getUnits());
                editBg.setValue(bg);
            }
        });

        Integer maxCarbs = MainApp.getConfigBuilder().applyCarbsConstraints(Constants.carbsOnlyForCheckLimit);
        editCarbs = (NumberPicker) view.findViewById(R.id.careportal_newnstreatment_carbsinput);
        editCarbs.setParams(0d, 0d, (double) maxCarbs, 1d, new DecimalFormat("0"), false);

        Double maxInsulin = MainApp.getConfigBuilder().applyBolusConstraints(Constants.bolusOnlyForCheckLimit);
        editInsulin = (NumberPicker) view.findViewById(R.id.careportal_newnstreatment_insulininput);
        editInsulin.setParams(0d, 0d, maxInsulin, 0.05d, new DecimalFormat("0.00"), false);

        editSplit = (NumberPicker) view.findViewById(R.id.careportal_newnstreatment_splitinput);
        editSplit.setParams(100d, 0d, 100d, 5d, new DecimalFormat("0"), true);
        editDuration = (NumberPicker) view.findViewById(R.id.careportal_newnstreatment_durationinput);
        editDuration.setParams(0d, 0d, 24 * 60d, 10d, new DecimalFormat("0"), false);

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

        Integer maxPercent = MainApp.getConfigBuilder().applyBasalConstraints(Constants.basalPercentOnlyForCheckLimit);
        editPercent = (NumberPicker) view.findViewById(R.id.careportal_newnstreatment_percentinput);
        editPercent.setParams(0d, 0d, (double) maxPercent, 5d, new DecimalFormat("0"), true, percentTextWatcher);

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

        Double maxAbsolute = MainApp.getConfigBuilder().applyBasalConstraints(Constants.basalAbsoluteOnlyForCheckLimit);
        editAbsolute = (NumberPicker) view.findViewById(R.id.careportal_newnstreatment_absoluteinput);
        editAbsolute.setParams(0d, 0d, maxAbsolute, 0.05d, new DecimalFormat("0.00"), true, absoluteTextWatcher);

        editCarbTime = (NumberPicker) view.findViewById(R.id.careportal_newnstreatment_carbtimeinput);
        editCarbTime.setParams(0d, -60d, 60d, 5d, new DecimalFormat("0"), false);

        editPercentage = (NumberPicker) view.findViewById(R.id.careportal_newnstreatment_percentage);
        editPercentage.setParams(100d, (double) Constants.CPP_MIN_PERCENTAGE, (double) Constants.CPP_MAX_PERCENTAGE, 1d, new DecimalFormat("0"), false);

        editTimeshift = (NumberPicker) view.findViewById(R.id.careportal_newnstreatment_timeshift);
        editTimeshift.setParams(0d, (double) Constants.CPP_MIN_TIMESHIFT, (double) Constants.CPP_MAX_TIMESHIFT, 1d, new DecimalFormat("0"), false);

        ProfileSwitch ps = MainApp.getConfigBuilder().getProfileSwitchFromHistory(System.currentTimeMillis());
        if (ps != null && ps.isCPP) {
            final int percentage = ps.percentage;
            final int timeshift = ps.timeshift;
            reuseButton.setText(reuseButton.getText() + " " + percentage + "% " + timeshift + "h");
            reuseButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    editPercentage.setValue((double) percentage);
                    editTimeshift.setValue((double) timeshift);
                }
            });
        }

        showOrHide((ViewGroup) view.findViewById(R.id.careportal_newnstreatment_eventtime_layout), options.date);
        showOrHide((ViewGroup) view.findViewById(R.id.careportal_newnstreatment_bg_layout), options.bg);
        showOrHide((ViewGroup) view.findViewById(R.id.careportal_newnstreatment_bgsource_layout), options.bg);
        showOrHide((ViewGroup) view.findViewById(R.id.careportal_newnstreatment_insulin_layout), options.insulin);
        showOrHide((ViewGroup) view.findViewById(R.id.careportal_newnstreatment_carbs_layout), options.carbs);
        showOrHide((ViewGroup) view.findViewById(R.id.careportal_newnstreatment_split_layout), options.split);
        showOrHide((ViewGroup) view.findViewById(R.id.careportal_newnstreatment_duration_layout), options.duration);
        showOrHide(layoutPercent, options.percent);
        showOrHide(layoutAbsolute, options.absolute);
        showOrHide((ViewGroup) view.findViewById(R.id.careportal_newnstreatment_carbtime_layout), options.prebolus);
        showOrHide((ViewGroup) view.findViewById(R.id.careportal_newnstreatment_profile_layout), options.profile);
        showOrHide((ViewGroup) view.findViewById(R.id.careportal_newnstreatment_percentage_layout), options.profile);
        showOrHide((ViewGroup) view.findViewById(R.id.careportal_newnstreatment_timeshift_layout), options.profile);
        showOrHide((ViewGroup) view.findViewById(R.id.careportal_newnstreatment_reuse_layout), options.profile && ps != null && ps.isCPP);
        showOrHide((ViewGroup) view.findViewById(R.id.careportal_newnstreatment_temptarget_layout), options.tempTarget);

        setCancelable(true);
        getDialog().setCanceledOnTouchOutside(false);
        return view;
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
                dpd.show(context.getFragmentManager(), "Datepickerdialog");
                break;
            case R.id.careportal_newnstreatment_eventtime:
                TimePickerDialog tpd = TimePickerDialog.newInstance(
                        this,
                        calendar.get(Calendar.HOUR_OF_DAY),
                        calendar.get(Calendar.MINUTE),
                        DateFormat.is24HourFormat(context)
                );
                tpd.setThemeDark(true);
                tpd.dismissOnPause(true);
                tpd.show(context.getFragmentManager(), "Timepickerdialog");
                break;
            case R.id.ok:
                createNSTreatment();
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

    @Override
    public void onDateSet(DatePickerDialog view, int year, int monthOfYear, int dayOfMonth) {
        eventTime.setYear(year - 1900);
        eventTime.setMonth(monthOfYear);
        eventTime.setDate(dayOfMonth);
        dateButton.setText(DateUtil.dateString(eventTime));
    }

    @Override
    public void onTimeSet(RadialPickerLayout view, int hourOfDay, int minute, int second) {
        eventTime.setHours(hourOfDay);
        eventTime.setMinutes(minute);
        eventTime.setSeconds(second);
        timeButton.setText(DateUtil.timeString(eventTime));
    }


    JSONObject gatherData() {
        String enteredBy = SP.getString("careportal_enteredby", "");
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
            data.put("units", units);
            if (!enteredBy.equals("")) data.put("enteredBy", enteredBy);
            if (options.eventType == R.id.careportal_combobolus) {
                Double enteredInsulin = SafeParse.stringToDouble(editInsulin.getText());
                data.put("enteredinsulin", enteredInsulin);
                data.put("insulin", enteredInsulin * SafeParse.stringToDouble(editSplit.getText()) / 100);
                data.put("relative", enteredInsulin * (100 - SafeParse.stringToDouble(editSplit.getText())) / 100 / SafeParse.stringToDouble(editDuration.getText()) * 60);
            }
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return data;
    }

    String buildConfirmText(JSONObject data) {
        String ret = "";
        try {
            if (data.has("eventType")) {
                ret += getString(R.string.careportal_newnstreatment_eventtype);
                ret += ": ";
                ret += Translator.translate(data.getString("eventType"));
                ret += "\n";
            }
            if (data.has("glucose")) {
                ret += getString(R.string.treatments_wizard_bg_label);
                ret += ": ";
                ret += data.get("glucose");
                ret += " " + units + "\n";
            }
            if (data.has("glucoseType")) {
                ret += getString(R.string.careportal_newnstreatment_glucosetype);
                ret += ": ";
                ret += Translator.translate(data.getString("glucoseType"));
                ret += "\n";
            }
            if (data.has("carbs")) {
                ret += getString(R.string.careportal_newnstreatment_carbs_label);
                ret += ": ";
                ret += data.get("carbs");
                ret += " g\n";
            }
            if (data.has("insulin")) {
                ret += getString(R.string.careportal_newnstreatment_insulin_label);
                ret += ": ";
                ret += data.get("insulin");
                ret += " U\n";
            }
            if (data.has("duration")) {
                ret += getString(R.string.careportal_newnstreatment_duration_label);
                ret += ": ";
                ret += data.get("duration");
                ret += " min\n";
            }
            if (data.has("percent")) {
                ret += getString(R.string.careportal_newnstreatment_percent_label);
                ret += ": ";
                ret += data.get("percent");
                ret += " %\n";
            }
            if (data.has("absolute")) {
                ret += getString(R.string.careportal_newnstreatment_absolute_label);
                ret += ": ";
                ret += data.get("absolute");
                ret += " U/h\n";
            }
            if (data.has("preBolus")) {
                ret += getString(R.string.careportal_newnstreatment_carbtime_label);
                ret += ": ";
                ret += data.get("preBolus");
                ret += " min\n";
            }
            if (data.has("notes")) {
                ret += getString(R.string.careportal_newnstreatment_notes_label);
                ret += ": ";
                ret += data.get("notes");
                ret += "\n";
            }
            if (data.has("profile")) {
                ret += getString(R.string.careportal_newnstreatment_profile_label);
                ret += ": ";
                ret += data.get("profile");
                ret += "\n";
            }
            if (data.has("percentage")) {
                ret += getString(R.string.careportal_newnstreatment_percentage_label);
                ret += ": ";
                ret += data.get("percentage");
                ret += " %\n";
            }
            if (data.has("timeshift")) {
                ret += getString(R.string.careportal_newnstreatment_timeshift_label);
                ret += ": ";
                ret += data.get("timeshift");
                ret += " h\n";
            }
            if (data.has("targetBottom") && data.has("targetTop")) {
                ret += getString(R.string.target_range);
                ret += " ";
                ret += data.get("targetBottom");
                ret += " - ";
                ret += data.get("targetTop");
                ret += "\n";
            }
            if (data.has("created_at")) {
                ret += getString(R.string.careportal_newnstreatment_eventtime_label);
                ret += ": ";
                ret += eventTime.toLocaleString();
                ret += "\n";
            }
            if (data.has("enteredBy")) {
                ret += getString(R.string.careportal_newnstreatment_enteredby_title);
                ret += ": ";
                ret += data.get("enteredBy");
                ret += "\n";
            }
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }

        return ret;
    }

    void createNSTreatment() {
        final JSONObject data = gatherData();
        String confirmText = buildConfirmText(data);
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(getContext().getString(R.string.confirmation));
        builder.setMessage(confirmText);
        builder.setPositiveButton(getContext().getString(R.string.ok), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                if (options.executeProfileSwitch) {
                    if (data.has("profile")) {
                        try {
                            doProfileSwitch(profileStore, data.getString("profile"), data.getInt("duration"), data.getInt("percentage"), data.getInt("timeshift"));
                        } catch (JSONException e) {
                            log.error("Unhandled exception", e);
                        }
                    }
                } else if (options.executeTempTarget) {
                    try {
                        if ((data.has("targetBottom") && data.has("targetTop")) || (data.has("duration") && data.getInt("duration") == 0)) {
                            TempTarget tempTarget = new TempTarget();
                            tempTarget.date = eventTime.getTime();
                            tempTarget.durationInMinutes = data.getInt("duration");
                            tempTarget.reason = data.getString("reason");
                            tempTarget.source = Source.USER;
                            if (tempTarget.durationInMinutes != 0) {
                                tempTarget.low = Profile.toMgdl(data.getDouble("targetBottom"), profile.getUnits());
                                tempTarget.high = Profile.toMgdl(data.getDouble("targetTop"), profile.getUnits());
                            } else {
                                tempTarget.low = 0;
                                tempTarget.high = 0;
                            }
                            log.debug("Creating new TempTarget db record: " + tempTarget.toString());
                            MainApp.getDbHelper().createOrUpdate(tempTarget);
                            NSUpload.uploadCareportalEntryToNS(data);
                            Answers.getInstance().logCustom(new CustomEvent("TempTarget"));
                        }
                    } catch (JSONException e) {
                        log.error("Unhandled exception", e);
                    }
                } else {
                    NSUpload.uploadCareportalEntryToNS(data);
                    Answers.getInstance().logCustom(new CustomEvent("NSTreatment"));
                }
            }
        });
        builder.setNegativeButton(getContext().getString(R.string.cancel), null);
        builder.show();
    }

    public static void doProfileSwitch(final ProfileStore profileStore, final String profileName, final int duration, final int percentage, final int timeshift) {
        ProfileSwitch profileSwitch = new ProfileSwitch();
        profileSwitch.date = System.currentTimeMillis();
        profileSwitch.source = Source.USER;
        profileSwitch.profileName = profileName;
        profileSwitch.profileJson = profileStore.getSpecificProfile(profileName).getData().toString();
        profileSwitch.profilePlugin = ConfigBuilderPlugin.getActiveProfileInterface().getClass().getName();
        profileSwitch.durationInMinutes = duration;
        profileSwitch.isCPP = percentage != 100 || timeshift != 0;
        profileSwitch.timeshift = timeshift;
        profileSwitch.percentage = percentage;
        MainApp.getConfigBuilder().addToHistoryProfileSwitch(profileSwitch);

        ConfigBuilderPlugin.getCommandQueue().setProfile(profileSwitch.getProfileObject(), new Callback() {
            @Override
            public void run() {
                if (!result.success) {
                    Intent i = new Intent(MainApp.instance(), ErrorHelperActivity.class);
                    i.putExtra("soundid", R.raw.boluserror);
                    i.putExtra("status", result.comment);
                    i.putExtra("title", MainApp.sResources.getString(R.string.failedupdatebasalprofile));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    MainApp.instance().startActivity(i);
                }
                MainApp.bus().post(new EventNewBasalProfile());
            }
        });
        Answers.getInstance().logCustom(new CustomEvent("ProfileSwitch"));
    }

    public static void doProfileSwitch(final int duration, final int percentage, final int timeshift) {
        ProfileSwitch profileSwitch = MainApp.getConfigBuilder().getProfileSwitchFromHistory(System.currentTimeMillis());
        if (profileSwitch != null) {
            profileSwitch = new ProfileSwitch();
            profileSwitch.date = System.currentTimeMillis();
            profileSwitch.source = Source.USER;
            profileSwitch.profileName = MainApp.getConfigBuilder().getProfileName(System.currentTimeMillis(), false);
            profileSwitch.profileJson = MainApp.getConfigBuilder().getProfile().getData().toString();
            profileSwitch.profilePlugin = ConfigBuilderPlugin.getActiveProfileInterface().getClass().getName();
            profileSwitch.durationInMinutes = duration;
            profileSwitch.isCPP = percentage != 100 || timeshift != 0;
            profileSwitch.timeshift = timeshift;
            profileSwitch.percentage = percentage;
            MainApp.getConfigBuilder().addToHistoryProfileSwitch(profileSwitch);

            ConfigBuilderPlugin.getCommandQueue().setProfile(profileSwitch.getProfileObject(), new Callback() {
                @Override
                public void run() {
                    if (!result.success) {
                        Intent i = new Intent(MainApp.instance(), ErrorHelperActivity.class);
                        i.putExtra("soundid", R.raw.boluserror);
                        i.putExtra("status", result.comment);
                        i.putExtra("title", MainApp.sResources.getString(R.string.failedupdatebasalprofile));
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        MainApp.instance().startActivity(i);
                    }
                    MainApp.bus().post(new EventNewBasalProfile());
                }
            });
            Answers.getInstance().logCustom(new CustomEvent("ProfileSwitch"));
        } else {
            log.error("No profile switch existing");
        }
    }

}
