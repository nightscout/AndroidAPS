package info.nightscout.androidaps.plugins.general.careportal.Dialogs;


import android.app.Activity;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.common.collect.Lists;
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
import java.util.List;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.ProfileStore;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.ProfileSwitch;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.careportal.OptionsToShow;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.DefaultValueHelper;
import info.nightscout.androidaps.utils.HardLimits;
import info.nightscout.androidaps.utils.JsonHelper;
import info.nightscout.androidaps.utils.NumberPicker;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.SafeParse;
import info.nightscout.androidaps.utils.Translator;

public class NewNSTreatmentDialog extends DialogFragment implements View.OnClickListener, DatePickerDialog.OnDateSetListener, TimePickerDialog.OnTimeSetListener {
    private static Logger log = LoggerFactory.getLogger(NewNSTreatmentDialog.class);

    private Activity context;

    private static OptionsToShow options;
    private static String event;

    Profile profile;
    public ProfileStore profileStore;
    String units = Constants.MGDL;

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

    public void setOptions(OptionsToShow options, int event) {
        this.options = options;
        this.event = MainApp.gs(event);
    }

    public NewNSTreatmentDialog() {
        super();

        if (seconds == null) {
            seconds = Double.valueOf(Math.random() * 59).intValue();
        }
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
        getDialog().setTitle(MainApp.gs(options.eventName));
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
        profile = ProfileFunctions.getInstance().getProfile();
        profileStore = ConfigBuilderPlugin.getPlugin().getActiveProfileInterface().getProfile();
        if (profileStore == null) {
            if (options.eventType == R.id.careportal_profileswitch) {
                log.error("Profile switch called but plugin doesn't contain valid profile");
            }
        } else {
            ArrayList<CharSequence> profileList;
            units = profile != null ? profile.getUnits() : Constants.MGDL;
            profileList = profileStore.getProfileList();
            ArrayAdapter<CharSequence> adapter = new ArrayAdapter<>(getContext(),
                    R.layout.spinner_centered, profileList);
            profileSpinner.setAdapter(adapter);
            // set selected to actual profile
            for (int p = 0; p < profileList.size(); p++) {
                if (profileList.get(p).equals(ProfileFunctions.getInstance().getProfileName(false)))
                    profileSpinner.setSelection(p);
            }
        }
        final Double bg = Profile.fromMgdlToUnits(GlucoseStatus.getGlucoseStatusData() != null ? GlucoseStatus.getGlucoseStatusData().glucose : 0d, units);

        // temp target
        final List<String> reasonList = Lists.newArrayList(
                MainApp.gs(R.string.manual),
                MainApp.gs(R.string.eatingsoon),
                MainApp.gs(R.string.activity),
                MainApp.gs(R.string.hypo));
        ArrayAdapter<String> adapterReason = new ArrayAdapter<>(getContext(),
                R.layout.spinner_centered, reasonList);
        reasonSpinner.setAdapter(adapterReason);
        reasonSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                double defaultDuration;
                double defaultTarget = 0;
                if (profile != null && editTemptarget.getValue() == bg) {
                    defaultTarget = bg;
                } else {
                    //prevent changes on screen rotate
                    defaultTarget = editTemptarget.getValue();
                }
                boolean erase = false;

                String units = ProfileFunctions.getInstance().getProfileUnits();
                DefaultValueHelper helper = new DefaultValueHelper();
                if (MainApp.gs(R.string.eatingsoon).equals(reasonList.get(position))) {
                    defaultDuration = helper.determineEatingSoonTTDuration();
                    defaultTarget = helper.determineEatingSoonTT(units);
                } else if (MainApp.gs(R.string.activity).equals(reasonList.get(position))) {
                    defaultDuration = helper.determineActivityTTDuration();
                    defaultTarget = helper.determineActivityTT(units);
                } else if (MainApp.gs(R.string.hypo).equals(reasonList.get(position))) {
                    defaultDuration = helper.determineHypoTTDuration();
                    defaultTarget = helper.determineHypoTT(units);
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
        editBg = view.findViewById(R.id.careportal_newnstreatment_bginput);
        editTemptarget = view.findViewById(R.id.careportal_newnstreatment_temptarget);
        if (profile == null) {
            editBg.setParams(bg, 0d, 500d, 0.1d, new DecimalFormat("0"), false, view.findViewById(R.id.ok), bgTextWatcher);
            editTemptarget.setParams(Constants.MIN_TT_MGDL, Constants.MIN_TT_MGDL, Constants.MAX_TT_MGDL, 0.1d, new DecimalFormat("0.0"), false, view.findViewById(R.id.ok));
        } else if (units.equals(Constants.MMOL)) {
            editBg.setParams(bg, 0d, 30d, 0.1d, new DecimalFormat("0.0"), false, view.findViewById(R.id.ok), bgTextWatcher);
            editTemptarget.setParams(Constants.MIN_TT_MMOL, Constants.MIN_TT_MMOL, Constants.MAX_TT_MMOL, 0.1d, new DecimalFormat("0.0"), false, view.findViewById(R.id.ok));
        } else {
            editBg.setParams(bg, 0d, 500d, 1d, new DecimalFormat("0"), false, view.findViewById(R.id.ok), bgTextWatcher);
            editTemptarget.setParams(Constants.MIN_TT_MGDL, Constants.MIN_TT_MGDL, Constants.MAX_TT_MGDL, 1d, new DecimalFormat("0"), false, view.findViewById(R.id.ok));
        }

        sensorRadioButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Double bg1 = Profile.fromMgdlToUnits(GlucoseStatus.getGlucoseStatusData() != null ? GlucoseStatus.getGlucoseStatusData().glucose : 0d, units);
            if (savedInstanceState != null && savedInstanceState.getDouble("editBg") != bg1) {
                editBg.setValue(savedInstanceState.getDouble("editBg"));
            } else {
                editBg.setValue(bg1);
            }
        });

        Integer maxCarbs = MainApp.getConstraintChecker().getMaxCarbsAllowed().value();
        editCarbs = (NumberPicker) view.findViewById(R.id.careportal_newnstreatment_carbsinput);
        editCarbs.setParams(0d, 0d, (double) maxCarbs, 1d, new DecimalFormat("0"), false, view.findViewById(R.id.ok));

        Double maxInsulin = MainApp.getConstraintChecker().getMaxBolusAllowed().value();
        editInsulin = (NumberPicker) view.findViewById(R.id.careportal_newnstreatment_insulininput);
        editInsulin.setParams(0d, 0d, maxInsulin, 0.05d, new DecimalFormat("0.00"), false, view.findViewById(R.id.ok));

        editSplit = (NumberPicker) view.findViewById(R.id.careportal_newnstreatment_splitinput);
        editSplit.setParams(100d, 0d, 100d, 5d, new DecimalFormat("0"), true, view.findViewById(R.id.ok));
        editDuration = (NumberPicker) view.findViewById(R.id.careportal_newnstreatment_durationinput);
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
            maxPercent = MainApp.getConstraintChecker().getMaxBasalPercentAllowed(profile).value();
        editPercent = (NumberPicker) view.findViewById(R.id.careportal_newnstreatment_percentinput);
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

        Double maxAbsolute = HardLimits.maxBasal();
        if (profile != null)
            maxAbsolute = MainApp.getConstraintChecker().getMaxBasalAllowed(profile).value();
        editAbsolute = (NumberPicker) view.findViewById(R.id.careportal_newnstreatment_absoluteinput);
        editAbsolute.setParams(0d, 0d, maxAbsolute, 0.05d, new DecimalFormat("0.00"), true, view.findViewById(R.id.ok), absoluteTextWatcher);

        editCarbTime = (NumberPicker) view.findViewById(R.id.careportal_newnstreatment_carbtimeinput);
        editCarbTime.setParams(0d, -60d, 60d, 5d, new DecimalFormat("0"), false, view.findViewById(R.id.ok));

        editPercentage = (NumberPicker) view.findViewById(R.id.careportal_newnstreatment_percentage);
        editPercentage.setParams(100d, (double) Constants.CPP_MIN_PERCENTAGE, (double) Constants.CPP_MAX_PERCENTAGE, 1d, new DecimalFormat("0"), false, view.findViewById(R.id.ok));

        editTimeshift = (NumberPicker) view.findViewById(R.id.careportal_newnstreatment_timeshift);
        editTimeshift.setParams(0d, (double) Constants.CPP_MIN_TIMESHIFT, (double) Constants.CPP_MAX_TIMESHIFT, 1d, new DecimalFormat("0"), false, view.findViewById(R.id.ok));

        ProfileSwitch ps = TreatmentsPlugin.getPlugin().getProfileSwitchFromHistory(DateUtil.now());
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
            editBg.setValue(Profile.fromMgdlToUnits(data.get(0).value, units));
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
    public void onTimeSet(RadialPickerLayout view, int hourOfDay, int minute, int second) {
        eventTime.setHours(hourOfDay);
        eventTime.setMinutes(minute);
        eventTime.setSeconds(this.seconds++); // randomize seconds to prevent creating record of the same time, if user choose time manually
        timeButton.setText(DateUtil.timeString(eventTime));
        updateBGforDateTime();
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
        if (data.has("eventType")) {
            ret += MainApp.gs(R.string.careportal_newnstreatment_eventtype);
            ret += ": ";
            ret += Translator.translate(JsonHelper.safeGetString(data, "eventType", ""));
            ret += "\n";
        }
        if (data.has("glucose")) {
            ret += MainApp.gs(R.string.treatments_wizard_bg_label);
            ret += ": ";
            ret += JsonHelper.safeGetObject(data, "glucose", "");
            ret += " " + units + "\n";
        }
        if (data.has("glucoseType")) {
            ret += MainApp.gs(R.string.careportal_newnstreatment_glucosetype);
            ret += ": ";
            ret += Translator.translate(JsonHelper.safeGetString(data, "glucoseType", ""));
            ret += "\n";
        }
        if (data.has("carbs")) {
            ret += MainApp.gs(R.string.careportal_newnstreatment_carbs_label);
            ret += ": ";
            ret += JsonHelper.safeGetObject(data, "carbs", "");
            ret += " g\n";
        }
        if (data.has("insulin")) {
            ret += MainApp.gs(R.string.careportal_newnstreatment_insulin_label);
            ret += ": ";
            ret += JsonHelper.safeGetObject(data, "insulin", "");
            ret += " U\n";
        }
        if (data.has("duration")) {
            ret += MainApp.gs(R.string.careportal_newnstreatment_duration_label);
            ret += ": ";
            ret += JsonHelper.safeGetObject(data, "duration", "");
            ret += " min\n";
        }
        if (data.has("percent")) {
            ret += MainApp.gs(R.string.careportal_newnstreatment_percent_label);
            ret += ": ";
            ret += JsonHelper.safeGetObject(data, "percent", "");
            ret += " %\n";
        }
        if (data.has("absolute")) {
            ret += MainApp.gs(R.string.careportal_newnstreatment_absolute_label);
            ret += ": ";
            ret += JsonHelper.safeGetObject(data, "absolute", "");
            ret += " U/h\n";
        }
        if (data.has("preBolus")) {
            ret += MainApp.gs(R.string.careportal_newnstreatment_carbtime_label);
            ret += ": ";
            ret += JsonHelper.safeGetObject(data, "preBolus", "");
            ret += " min\n";
        }
        if (data.has("notes")) {
            ret += MainApp.gs(R.string.careportal_newnstreatment_notes_label);
            ret += ": ";
            ret += JsonHelper.safeGetObject(data, "notes", "");
            ret += "\n";
        }
        if (data.has("profile")) {
            ret += MainApp.gs(R.string.careportal_newnstreatment_profile_label);
            ret += ": ";
            ret += JsonHelper.safeGetObject(data, "profile", "");
            ret += "\n";
        }
        if (data.has("percentage")) {
            ret += MainApp.gs(R.string.careportal_newnstreatment_percentage_label);
            ret += ": ";
            ret += JsonHelper.safeGetObject(data, "percentage", "");
            ret += " %\n";
        }
        if (data.has("timeshift")) {
            ret += MainApp.gs(R.string.careportal_newnstreatment_timeshift_label);
            ret += ": ";
            ret += JsonHelper.safeGetObject(data, "timeshift", "");
            ret += " h\n";
        }
        if (data.has("targetBottom") && data.has("targetTop")) {
            ret += MainApp.gs(R.string.target_range);
            ret += " ";
            ret += JsonHelper.safeGetObject(data, "targetBottom", "");
            ret += " - ";
            ret += JsonHelper.safeGetObject(data, "targetTop", "");
            ret += "\n";
        }
        if (data.has("created_at")) {
            ret += MainApp.gs(R.string.careportal_newnstreatment_eventtime_label);
            ret += ": ";
            ret += eventTime.toLocaleString();
            ret += "\n";
        }
        if (data.has("enteredBy")) {
            ret += MainApp.gs(R.string.careportal_newnstreatment_enteredby_title);
            ret += ": ";
            ret += JsonHelper.safeGetObject(data, "enteredBy", "");
            ret += "\n";
        }

        return ret;
    }

    void confirmNSTreatmentCreation() {
        if (context != null) {
            final JSONObject data = gatherData();
            final String confirmText = buildConfirmText(data);
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(MainApp.gs(R.string.confirmation));
            builder.setMessage(confirmText);
            builder.setPositiveButton(MainApp.gs(R.string.ok), (dialog, id) -> createNSTreatment(data));
            builder.setNegativeButton(MainApp.gs(R.string.cancel), null);
            builder.show();
        }
    }


    public void createNSTreatment(JSONObject data) {
        if (options.executeProfileSwitch) {
            if (data.has("profile")) {
                ProfileFunctions.doProfileSwitch(profileStore, JsonHelper.safeGetString(data, "profile"), JsonHelper.safeGetInt(data, "duration"), JsonHelper.safeGetInt(data, "percentage"), JsonHelper.safeGetInt(data, "timeshift"));
            }
        } else if (options.executeTempTarget) {
            final int duration = JsonHelper.safeGetInt(data, "duration");
            final double targetBottom = JsonHelper.safeGetDouble(data, "targetBottom");
            final double targetTop = JsonHelper.safeGetDouble(data, "targetTop");
            final String reason = JsonHelper.safeGetString(data, "reason", "");
            if ((targetBottom != 0d && targetTop != 0d) || duration == 0) {
                TempTarget tempTarget = new TempTarget()
                        .date(eventTime.getTime())
                        .duration(duration)
                        .reason(reason)
                        .source(Source.USER);
                if (tempTarget.durationInMinutes != 0) {
                    tempTarget.low(Profile.toMgdl(targetBottom, units))
                            .high(Profile.toMgdl(targetTop, units));
                } else {
                    tempTarget.low(0).high(0);
                }
                TreatmentsPlugin.getPlugin().addToHistoryTempTarget(tempTarget);
            }
            if (duration == 10)
                SP.putBoolean(R.string.key_objectiveusetemptarget, true);
        } else {
            if (JsonHelper.safeGetString(data, "eventType").equals(CareportalEvent.PROFILESWITCH)) {
                ProfileSwitch profileSwitch = ProfileFunctions.prepareProfileSwitch(
                        profileStore,
                        JsonHelper.safeGetString(data, "profile"),
                        JsonHelper.safeGetInt(data, "duration"),
                        JsonHelper.safeGetInt(data, "percentage"),
                        JsonHelper.safeGetInt(data, "timeshift"),
                        eventTime.getTime()
                );
                NSUpload.uploadProfileSwitch(profileSwitch);
            } else {
                NSUpload.uploadCareportalEntryToNS(data);
            }
        }
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
