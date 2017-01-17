package info.nightscout.androidaps.plugins.Careportal.Dialogs;


import android.app.Activity;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;

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
import info.nightscout.androidaps.events.EventNewBasalProfile;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.Careportal.OptionsToShow;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.client.data.NSProfile;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.PlusMinusEditText;
import info.nightscout.utils.SafeParse;
import info.nightscout.utils.ToastUtils;
import info.nightscout.utils.Translator;

public class NewNSTreatmentDialog extends DialogFragment implements View.OnClickListener, DatePickerDialog.OnDateSetListener, TimePickerDialog.OnTimeSetListener {
    private static Logger log = LoggerFactory.getLogger(NewNSTreatmentDialog.class);

    private FragmentActivity context;

    private static OptionsToShow options;

    NSProfile profile;
    String units;

    LinearLayout layoutBg;
    LinearLayout layoutBgSource;
    LinearLayout layoutInsulin;
    LinearLayout layoutCarbs;
    LinearLayout layoutSplit;
    LinearLayout layoutDuration;
    LinearLayout layoutPercent;
    LinearLayout layoutAbsolute;
    LinearLayout layoutCarbTime;
    LinearLayout layoutProfile;
    Button dateButton;
    Button timeButton;
    Button okButton;

    TextView bgUnitsView;
    RadioButton meterRadioButton;
    RadioButton sensorRadioButton;
    RadioButton otherRadioButton;
    EditText notesEdit;
    EditText bgInputEdit;
    EditText insulinEdit;
    EditText carbsEdit;
    EditText percentEdit;
    EditText absoluteEdit;
    EditText durationeEdit;
    EditText carbTimeEdit;
    EditText splitEdit;
    Spinner profileSpinner;

    PlusMinusEditText editBg;
    PlusMinusEditText editCarbs;
    PlusMinusEditText editInsulin;
    PlusMinusEditText editSplit;
    PlusMinusEditText editDuration;
    PlusMinusEditText editPercent;
    PlusMinusEditText editAbsolute;
    PlusMinusEditText editCarbTime;

    Date eventTime;

    private static Handler sHandler;
    private static HandlerThread sHandlerThread;


    public void setOptions(OptionsToShow options) {
        this.options = options;
    }

    public NewNSTreatmentDialog() {
        super();
        if (sHandlerThread == null) {
            sHandlerThread = new HandlerThread(NewNSTreatmentDialog.class.getSimpleName());
            sHandlerThread.start();
            sHandler = new Handler(sHandlerThread.getLooper());
        }
    }

    @Override
    public void onAttach(Activity activity) {
        context = (FragmentActivity) activity;
        super.onAttach(activity);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        getDialog().setTitle(getString(options.eventName));
        View view = inflater.inflate(R.layout.careportal_newnstreatment_dialog, container, false);

        layoutBg = (LinearLayout) view.findViewById(R.id.careportal_newnstreatment_bg_layout);
        layoutBgSource = (LinearLayout) view.findViewById(R.id.careportal_newnstreatment_bgsource_layout);
        layoutInsulin = (LinearLayout) view.findViewById(R.id.careportal_newnstreatment_insulin_layout);
        layoutCarbs = (LinearLayout) view.findViewById(R.id.careportal_newnstreatment_carbs_layout);
        layoutSplit = (LinearLayout) view.findViewById(R.id.careportal_newnstreatment_split_layout);
        layoutDuration = (LinearLayout) view.findViewById(R.id.careportal_newnstreatment_duration_layout);
        layoutPercent = (LinearLayout) view.findViewById(R.id.careportal_newnstreatment_percent_layout);
        layoutAbsolute = (LinearLayout) view.findViewById(R.id.careportal_newnstreatment_absolute_layout);
        layoutCarbTime = (LinearLayout) view.findViewById(R.id.careportal_newnstreatment_carbtime_layout);
        layoutProfile = (LinearLayout) view.findViewById(R.id.careportal_newnstreatment_profile_layout);

        bgUnitsView = (TextView) view.findViewById(R.id.careportal_newnstreatment_bgunits);
        meterRadioButton = (RadioButton) view.findViewById(R.id.careportal_newnstreatment_meter);
        sensorRadioButton = (RadioButton) view.findViewById(R.id.careportal_newnstreatment_sensor);
        otherRadioButton = (RadioButton) view.findViewById(R.id.careportal_newnstreatment_other);
        profileSpinner = (Spinner) view.findViewById(R.id.careportal_newnstreatment_profile);

        bgInputEdit = (EditText) view.findViewById(R.id.careportal_newnstreatment_bginput);
        insulinEdit = (EditText) view.findViewById(R.id.careportal_newnstreatment_insulininput);
        carbsEdit = (EditText) view.findViewById(R.id.careportal_newnstreatment_carbsinput);
        percentEdit = (EditText) view.findViewById(R.id.careportal_newnstreatment_percentinput);
        percentEdit.addTextChangedListener(new TextWatcher() {
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
        });
        absoluteEdit = (EditText) view.findViewById(R.id.careportal_newnstreatment_absoluteinput);
        absoluteEdit.addTextChangedListener(new TextWatcher() {
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
        });
        durationeEdit = (EditText) view.findViewById(R.id.careportal_newnstreatment_durationinput);
        carbTimeEdit = (EditText) view.findViewById(R.id.careportal_newnstreatment_carbtimeinput);
        notesEdit = (EditText) view.findViewById(R.id.careportal_newnstreatment_notes);
        splitEdit = (EditText) view.findViewById(R.id.careportal_newnstreatment_splitinput);

        eventTime = new Date();
        dateButton = (Button) view.findViewById(R.id.careportal_newnstreatment_eventdate);
        timeButton = (Button) view.findViewById(R.id.careportal_newnstreatment_eventtime);
        dateButton.setText(DateUtil.dateString(eventTime));
        timeButton.setText(DateUtil.timeString(eventTime));
        dateButton.setOnClickListener(this);
        timeButton.setOnClickListener(this);

        okButton = (Button) view.findViewById(R.id.careportal_newnstreatment_ok);
        okButton.setOnClickListener(this);

        // BG
        profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();
        ArrayList<CharSequence> profileList;
        units = Constants.MGDL;
        if (profile == null) {
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), context.getString(R.string.noprofile));
            profileList = new ArrayList<CharSequence>();
        } else {
            units = profile.getUnits();
            profileList = profile.getProfileList();
        }
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(getContext(),
                android.R.layout.simple_spinner_item, profileList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        profileSpinner.setAdapter(adapter);
        if (profile != null) {
            // set selected to actual profile
            for (int p = 0; p < profileList.size(); p++) {
                if (profileList.get(p).equals(profile.getActiveProfile()))
                    profileSpinner.setSelection(p);
            }
        }

        bgUnitsView.setText(units);

        // Set BG if not old
//        BgReading lastBg = MainApp.getDbHelper().lastBg();
//        Double lastBgValue = 0d;
//        if (lastBg != null) {
//            lastBgValue = lastBg.valueToUnits(units);
//            sensorRadioButton.setChecked(true);
//        } else {
//            meterRadioButton.setChecked(true);
//        }

        if (units.equals(Constants.MMOL))
            editBg = new PlusMinusEditText(view, R.id.careportal_newnstreatment_bginput, R.id.careportal_newnstreatment_bg_plus, R.id.careportal_newnstreatment_bg_minus, 0d, 0d, 40d, 0.1d, new DecimalFormat("0.0"), false);
        else
            editBg = new PlusMinusEditText(view, R.id.careportal_newnstreatment_bginput, R.id.careportal_newnstreatment_bg_plus, R.id.careportal_newnstreatment_bg_minus, 0d, 0d, 500d, 1d, new DecimalFormat("0"), false);

        Integer maxCarbs = MainApp.getConfigBuilder().applyCarbsConstraints(Constants.carbsOnlyForCheckLimit);
        editCarbs = new PlusMinusEditText(view, R.id.careportal_newnstreatment_carbsinput, R.id.careportal_newnstreatment_carbs_plus, R.id.careportal_newnstreatment_carbs_minus, 0d, 0d, (double) maxCarbs, 1d, new DecimalFormat("0"), false);

        Double maxInsulin = MainApp.getConfigBuilder().applyBolusConstraints(Constants.bolusOnlyForCheckLimit);
        editInsulin = new PlusMinusEditText(view, R.id.careportal_newnstreatment_insulininput, R.id.careportal_newnstreatment_insulin_plus, R.id.careportal_newnstreatment_insulin_minus, 0d, 0d, maxInsulin, 0.05d, new DecimalFormat("0.00"), false);

        editSplit = new PlusMinusEditText(view, R.id.careportal_newnstreatment_splitinput, R.id.careportal_newnstreatment_split_plus, R.id.careportal_newnstreatment_split_minus, 100d, 0d, 100d, 5d, new DecimalFormat("0"), true);
        editDuration = new PlusMinusEditText(view, R.id.careportal_newnstreatment_durationinput, R.id.careportal_newnstreatment_duration_plus, R.id.careportal_newnstreatment_duration_minus, 0d, 0d, 24 * 60d, 10d, new DecimalFormat("0"), false);

        Integer maxPercent = MainApp.getConfigBuilder().applyBasalConstraints(Constants.basalPercentOnlyForCheckLimit);
        editPercent = new PlusMinusEditText(view, R.id.careportal_newnstreatment_percentinput, R.id.careportal_newnstreatment_percent_plus, R.id.careportal_newnstreatment_percent_minus, 0d, 0d, (double) maxPercent, 5d, new DecimalFormat("0"), true);

        Double maxAbsolute = MainApp.getConfigBuilder().applyBasalConstraints(Constants.basalAbsoluteOnlyForCheckLimit);
        editAbsolute = new PlusMinusEditText(view, R.id.careportal_newnstreatment_absoluteinput, R.id.careportal_newnstreatment_absolute_plus, R.id.careportal_newnstreatment_absolute_minus, 0d, 0d, maxAbsolute, 0.05d, new DecimalFormat("0.00"), true);

        editCarbTime = new PlusMinusEditText(view, R.id.careportal_newnstreatment_carbtimeinput, R.id.careportal_newnstreatment_carbtime_plus, R.id.careportal_newnstreatment_carbtime_minus, 0d, -60d, 60d, 5d, new DecimalFormat("0"), false);

        showOrHide(layoutBg, options.bg);
        showOrHide(layoutBgSource, options.bg);
        showOrHide(layoutInsulin, options.insulin);
        showOrHide(layoutCarbs, options.carbs);
        showOrHide(layoutSplit, options.split);
        showOrHide(layoutDuration, options.duration);
        showOrHide(layoutPercent, options.percent);
        showOrHide(layoutAbsolute, options.absolute);
        showOrHide(layoutCarbTime, options.prebolus);
        showOrHide(layoutProfile, options.profile);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getDialog() != null)
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
                dpd.show(context.getFragmentManager(), "Datepickerdialog");
                break;
            case R.id.careportal_newnstreatment_eventtime:
                android.text.format.DateFormat df = new android.text.format.DateFormat();
                TimePickerDialog tpd = TimePickerDialog.newInstance(
                        this,
                        calendar.get(Calendar.HOUR_OF_DAY),
                        calendar.get(Calendar.MINUTE),
                        df.is24HourFormat(context)
                );
                tpd.setThemeDark(true);
                tpd.dismissOnPause(true);
                tpd.show(context.getFragmentManager(), "Timepickerdialog");
                break;
            case R.id.careportal_newnstreatment_ok:
                createNSTreatment();
                dismiss();
                break;
        }
    }

    private void showOrHide(LinearLayout layout, boolean visible) {
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
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        String enteredBy = SP.getString("careportal_enteredby", "");
        JSONObject data = new JSONObject();
        try {
            data.put("created_at", DateUtil.toISOString(eventTime));
            switch (options.eventType) {
                case R.id.careportal_bgcheck:
                    data.put("eventType", "BG Check");
                    break;
                case R.id.careportal_announcement:
                    data.put("eventType", "Announcement");
                    data.put("isAnnouncement", true);
                    break;
                case R.id.careportal_cgmsensorinsert:
                    data.put("eventType", "Sensor Change");
                    break;
                case R.id.careportal_cgmsensorstart:
                    data.put("eventType", "Sensor Start");
                    break;
                case R.id.careportal_combobolus:
                    data.put("splitNow", SafeParse.stringToDouble(splitEdit.getText().toString()));
                    data.put("splitExt", 100 - SafeParse.stringToDouble(splitEdit.getText().toString()));
                    data.put("eventType", "Combo Bolus");
                    break;
                case R.id.careportal_correctionbolus:
                    data.put("eventType", "Correction Bolus");
                    break;
                case R.id.careportal_carbscorrection:
                    data.put("eventType", "Carb Correction");
                    break;
                case R.id.careportal_exercise:
                    data.put("eventType", "Exercise");
                    break;
                case R.id.careportal_insulincartridgechange:
                    data.put("eventType", "Insulin Change");
                    break;
                case R.id.careportal_mealbolus:
                    data.put("eventType", "Meal Bolus");
                    break;
                case R.id.careportal_note:
                    data.put("eventType", "Note");
                    break;
                case R.id.careportal_profileswitch:
                    data.put("eventType", "Profile Switch");
                    break;
                case R.id.careportal_pumpsitechange:
                    data.put("eventType", "Site Change");
                    break;
                case R.id.careportal_question:
                    data.put("eventType", "Question");
                    break;
                case R.id.careportal_snackbolus:
                    data.put("eventType", "Snack Bolus");
                    break;
                case R.id.careportal_tempbasalstart:
                    data.put("eventType", "Temp Basal");
                    break;
                case R.id.careportal_tempbasalend:
                    data.put("eventType", "Temp Basal");
                    break;
                case R.id.careportal_openapsoffline:
                    data.put("eventType", "OpenAPS Offline");
                    break;
            }
            if (SafeParse.stringToDouble(bgInputEdit.getText().toString()) != 0d) {
                data.put("glucose", SafeParse.stringToDouble(bgInputEdit.getText().toString()));
                if (meterRadioButton.isChecked()) data.put("glucoseType", "Finger");
                if (sensorRadioButton.isChecked()) data.put("glucoseType", "Sensor");
                if (otherRadioButton.isChecked()) data.put("glucoseType", "Manual");
            }
            if (SafeParse.stringToDouble(carbsEdit.getText().toString()) != 0d)
                data.put("carbs", SafeParse.stringToDouble(carbsEdit.getText().toString()));
            if (SafeParse.stringToDouble(insulinEdit.getText().toString()) != 0d)
                data.put("insulin", SafeParse.stringToDouble(insulinEdit.getText().toString()));
            if (SafeParse.stringToDouble(durationeEdit.getText().toString()) != 0d)
                data.put("duration", SafeParse.stringToDouble(durationeEdit.getText().toString()));
            if (layoutPercent.getVisibility() != View.GONE)
                data.put("percent", SafeParse.stringToDouble(percentEdit.getText().toString()));
            if (layoutAbsolute.getVisibility() != View.GONE)
                data.put("absolute", SafeParse.stringToDouble(absoluteEdit.getText().toString()));
            if (options.profile && profileSpinner.getSelectedItem() != null)
                data.put("profile", profileSpinner.getSelectedItem().toString());
            if (SafeParse.stringToDouble(carbTimeEdit.getText().toString()) != 0d)
                data.put("preBolus", SafeParse.stringToDouble(carbTimeEdit.getText().toString()));
            if (!notesEdit.getText().toString().equals(""))
                data.put("notes", notesEdit.getText().toString());
            data.put("units", units);
            if (!enteredBy.equals("")) data.put("enteredBy", enteredBy);
            if (options.eventType == R.id.careportal_combobolus) {
                Double enteredInsulin = SafeParse.stringToDouble(insulinEdit.getText().toString());
                data.put("enteredinsulin", enteredInsulin);
                data.put("insulin", enteredInsulin * SafeParse.stringToDouble(splitEdit.getText().toString()) / 100);
                data.put("relative", enteredInsulin * (100 - SafeParse.stringToDouble(splitEdit.getText().toString())) / 100 / SafeParse.stringToDouble(durationeEdit.getText().toString()) * 60);
            }
        } catch (JSONException e) {
            e.printStackTrace();
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
            e.printStackTrace();
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
                        sHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    String profile = data.getString("profile");
                                    NSProfile nsProfile = MainApp.getConfigBuilder().getActiveProfile().getProfile();
                                    nsProfile.setActiveProfile(profile);
                                    PumpInterface pump = MainApp.getConfigBuilder();
                                    if (pump != null) {
                                        pump.setNewBasalProfile(nsProfile);
                                        log.debug("Setting new profile: " + profile);
                                        MainApp.bus().post(new EventNewBasalProfile(nsProfile));
                                    } else {
                                        log.error("No active pump selected");
                                    }
                                    ConfigBuilderPlugin.uploadCareportalEntryToNS(data);
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                    }
                } else {
                    ConfigBuilderPlugin.uploadCareportalEntryToNS(data);
                }
            }
        });
        builder.setNegativeButton(getContext().getString(R.string.cancel), null);
        builder.show();
    }

}
