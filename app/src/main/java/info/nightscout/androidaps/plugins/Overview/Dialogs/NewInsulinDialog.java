package info.nightscout.androidaps.plugins.Overview.Dialogs;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.HandlerThread;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.wdullaer.materialdatetimepicker.date.DatePickerDialog;
import com.wdullaer.materialdatetimepicker.time.RadialPickerLayout;
import com.wdullaer.materialdatetimepicker.time.TimePickerDialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.Date;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Loop.APSResult;
import info.nightscout.androidaps.plugins.OpenAPSSMB.DetermineBasalResultSMB;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.NumberPicker;
import info.nightscout.utils.SP;
import info.nightscout.utils.SafeParse;
import info.nightscout.utils.ToastUtils;

public class NewInsulinDialog extends DialogFragment implements OnClickListener, DatePickerDialog.OnDateSetListener, TimePickerDialog.OnTimeSetListener {
    private static Logger log = LoggerFactory.getLogger(NewInsulinDialog.class);

    private NumberPicker editInsulin;

    private TextView dateButton;
    private TextView timeButton;

    private Date initialEventTime;
    private Date eventTime;

    private Button plus1Button;
    private Button plus2Button;
    private Button plus3Button;

    public static final double PLUS1_DEFAULT = 0.5d;
    public static final double PLUS2_DEFAULT = 1d;
    public static final double PLUS3_DEFAULT = 2d;

    private CheckBox startESMCheckbox;
    private CheckBox recordOnlyCheckbox;

    private Double maxInsulin;

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
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            validateInputs();
        }
    };

    private void validateInputs() {
        Double insulin = SafeParse.stringToDouble(editInsulin.getText());
        if (insulin > maxInsulin) {
            editInsulin.setValue(0d);
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), getString(R.string.bolusconstraintapplied));
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

        maxInsulin = MainApp.getConfigBuilder().applyBolusConstraints(Constants.bolusOnlyForCheckLimit);

        editInsulin = (NumberPicker) view.findViewById(R.id.treatments_newinsulin_amount);

        editInsulin.setParams(0d, 0d, maxInsulin, ConfigBuilderPlugin.getActivePump().getPumpDescription().bolusStep, new DecimalFormat("0.00"), false, textWatcher);

        dateButton = (TextView) view.findViewById(R.id.newinsulin_eventdate);
        timeButton = (TextView) view.findViewById(R.id.newinsulin_eventtime);

        initialEventTime = new Date();
        eventTime = new Date(initialEventTime.getTime());
        dateButton.setText(DateUtil.dateString(eventTime));
        timeButton.setText(DateUtil.timeString(eventTime));
        dateButton.setOnClickListener(this);
        timeButton.setOnClickListener(this);

/*
        // This makes it to easy to just bolus insulinReq, which is almost always too much
        APSResult lastAPSResult = ConfigBuilderPlugin.getActiveAPS().getLastAPSResult();
        if (lastAPSResult != null && lastAPSResult instanceof DetermineBasalResultSMB && ((DetermineBasalResultSMB) lastAPSResult).insulinReq > 0) {
            editInsulin.setValue(((DetermineBasalResultSMB )lastAPSResult).insulinReq);
        }
*/

        plus1Button = (Button) view.findViewById(R.id.newinsulin_plus05);
        plus1Button.setOnClickListener(this);
        plus1Button.setText("+" + SP.getString(MainApp.gs(R.string.key_insulin_button_increment_1), String.valueOf(PLUS1_DEFAULT)));
        plus2Button = (Button) view.findViewById(R.id.newinsulin_plus10);
        plus2Button.setOnClickListener(this);
        plus2Button.setText("+" + SP.getString(MainApp.gs(R.string.key_insulin_button_increment_2), String.valueOf(PLUS2_DEFAULT)));
        plus3Button = (Button) view.findViewById(R.id.newinsulin_plus20);
        plus3Button.setOnClickListener(this);
        plus3Button.setText("+" + SP.getString(MainApp.gs(R.string.key_insulin_button_increment_3), String.valueOf(PLUS3_DEFAULT)));

        startESMCheckbox = (CheckBox) view.findViewById(R.id.newinsulin_start_eating_soon_tt);
        recordOnlyCheckbox = (CheckBox) view.findViewById(R.id.newinsulin_record_only);
        recordOnlyCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (dateButton != null) dateButton.setEnabled(isChecked);
            if (timeButton != null) timeButton.setEnabled(isChecked);
        });

        setCancelable(true);
        getDialog().setCanceledOnTouchOutside(false);
        return view;
    }

    @Override
    public synchronized void onClick(View view) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(eventTime);
        switch (view.getId()) {
            case R.id.ok:
                submit();
                break;
            case R.id.cancel:
                dismiss();
                break;
            case R.id.newinsulin_eventdate:
                DatePickerDialog dpd = DatePickerDialog.newInstance(
                        this,
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)
                );
                dpd.setThemeDark(true);
                dpd.dismissOnPause(true);
                dpd.show(getActivity().getFragmentManager(), "Datepickerdialog");
                break;
            case R.id.newinsulin_eventtime:
                TimePickerDialog tpd = TimePickerDialog.newInstance(
                        this,
                        calendar.get(Calendar.HOUR_OF_DAY),
                        calendar.get(Calendar.MINUTE),
                        DateFormat.is24HourFormat(getActivity())
                );
                tpd.setThemeDark(true);
                tpd.dismissOnPause(true);
                tpd.show(getActivity().getFragmentManager(), "Timepickerdialog");
                break;
            case R.id.newinsulin_start_eating_soon_tt:
                final Profile profile = MainApp.getConfigBuilder().getProfile();
                double tt = SP.getDouble(R.string.key_eatingsoon_target, 0d);
                double ttBgAdd = (tt - profile.getTargetLow()) / profile.getIsf();
                editInsulin.setValue(editInsulin.getValue() + (startESMCheckbox.isChecked() ? ttBgAdd : -ttBgAdd));
                break;
            case R.id.newinsulin_plus05:
                editInsulin.setValue(editInsulin.getValue()
                        + SP.getDouble(MainApp.gs(R.string.key_insulin_button_increment_1), PLUS1_DEFAULT));
                validateInputs();
                break;
            case R.id.newinsulin_plus10:
                editInsulin.setValue(editInsulin.getValue()
                        + SP.getDouble(MainApp.gs(R.string.key_insulin_button_increment_2), PLUS2_DEFAULT));
                validateInputs();
                break;
            case R.id.newinsulin_plus20:
                editInsulin.setValue(editInsulin.getValue()
                        + SP.getDouble(MainApp.gs(R.string.key_insulin_button_increment_3), PLUS3_DEFAULT));
                validateInputs();
                break;
        }
    }

    private void submit() {
        if (okClicked){
            log.debug("guarding: ok already clicked");
            dismiss();
            return;
        }
        okClicked = true;
        try {
            Double insulin = SafeParse.stringToDouble(editInsulin.getText());
            Double insulinAfterConstraints = MainApp.getConfigBuilder().applyBolusConstraints(insulin);

            String confirmMessage = "";
            if (insulin > 0) {
                confirmMessage += getString(R.string.bolus) + ": " + "<font color='" + MainApp.sResources.getColor(R.color.colorCarbsButton) + "'>" + insulinAfterConstraints + "U" + "</font>";
                if (recordOnlyCheckbox.isChecked()) {
                    confirmMessage += "<br/><font color='" + MainApp.sResources.getColor(R.color.low) + "'>" + "Bolus will be recorded only</font>";
                }
            }

            if (!insulinAfterConstraints.equals(insulin))
                confirmMessage += "<br/><font color='" + MainApp.sResources.getColor(R.color.low) + "'>" + getString(R.string.bolusconstraintapplied) + "</font>";
            double prefTTDuration = SP.getDouble(R.string.key_eatingsoon_duration, 45d);
            double ttDuration = prefTTDuration > 0 ? prefTTDuration : 45d;
            double prefTT = SP.getDouble(R.string.key_eatingsoon_target, 80d);
            double tt = prefTT > 0 ? prefTT : 80d;
            Profile currentProfile = MainApp.getConfigBuilder().getProfile();
            if(currentProfile.equals(null))
                return;
            if(currentProfile.getUnits().equals(Constants.MMOL)) {
                tt = prefTT > 0  ? Profile.toMgdl(prefTT, Constants.MGDL) : 80d;
            } else
            tt = prefTT > 0  ? prefTT : 80d;
            final double finalTT = tt;
            if (startESMCheckbox.isChecked()) {
                if(currentProfile.getUnits().equals("mmol")){
                    confirmMessage += "<br/>" + "TT: " + "<font color='" + MainApp.sResources.getColor(R.color.high) + "'>" + Profile.toMmol(tt, Constants.MMOL) + "mmol for " + ((int) ttDuration) + " min </font>";
                } else
                    confirmMessage += "<br/>" + "TT: " + "<font color='" + MainApp.sResources.getColor(R.color.high) + "'>" + ((int) tt) + "mg/dl for " + ((int) ttDuration) + " min </font>";
            }

            if (!initialEventTime.equals(eventTime)) {
                confirmMessage += "<br/>Time: " + DateUtil.dateAndTimeString(eventTime);
            }

            final double finalInsulinAfterConstraints = insulinAfterConstraints;

            final Context context = getContext();
            final AlertDialog.Builder builder = new AlertDialog.Builder(context);

            builder.setTitle(this.getContext().getString(R.string.confirmation));
            if (confirmMessage.startsWith("<br/>"))
                confirmMessage = confirmMessage.substring("<br/>".length());
            builder.setMessage(Html.fromHtml(confirmMessage));
            builder.setPositiveButton(getString(R.string.ok), (dialog, id) -> {
                synchronized (builder) {
                    if (accepted) {
                        log.debug("guarding: already accepted");
                        return;
                    }
                    accepted = true;

                    if (startESMCheckbox.isChecked()) {
                        TempTarget tempTarget = new TempTarget();
                        tempTarget.date = System.currentTimeMillis();
                        tempTarget.durationInMinutes = (int) ttDuration;
                        tempTarget.reason = "Eating soon";
                        tempTarget.source = Source.USER;
                        tempTarget.low = (int) finalTT;
                        tempTarget.high = (int) finalTT;
                        MainApp.getDbHelper().createOrUpdate(tempTarget);
                    }

                    if (finalInsulinAfterConstraints <= 0.01) {
                        return;
                    }

                    if (recordOnlyCheckbox.isChecked()) {
                        DetailedBolusInfo detailedBolusInfo = new DetailedBolusInfo();
                        detailedBolusInfo.source = Source.USER;
                        detailedBolusInfo.date = eventTime.getTime();
                        detailedBolusInfo.eventType = CareportalEvent.CORRECTIONBOLUS;
                        detailedBolusInfo.insulin = finalInsulinAfterConstraints;
                        MainApp.getConfigBuilder().addToHistoryTreatment(detailedBolusInfo);
                    } else {
                        DetailedBolusInfo detailedBolusInfo = new DetailedBolusInfo();
                        detailedBolusInfo.eventType = CareportalEvent.CORRECTIONBOLUS;
                        detailedBolusInfo.insulin = finalInsulinAfterConstraints;
                        detailedBolusInfo.context = context;
                        detailedBolusInfo.source = Source.USER;
                        ConfigBuilderPlugin.getCommandQueue().bolus(detailedBolusInfo, new Callback() {
                            @Override
                            public void run() {
                                if (!result.success) {
                                    Intent i = new Intent(MainApp.instance(), ErrorHelperActivity.class);
                                    i.putExtra("soundid", R.raw.boluserror);
                                    i.putExtra("status", result.comment);
                                    i.putExtra("title", MainApp.sResources.getString(R.string.treatmentdeliveryerror));
                                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    MainApp.instance().startActivity(i);
                                }
                            }
                        });
                        Answers.getInstance().logCustom(new CustomEvent("Bolus"));
                    }
                }
            });
            builder.setNegativeButton(getString(R.string.cancel), null);
            builder.show();
            dismiss();
        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }
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
}
