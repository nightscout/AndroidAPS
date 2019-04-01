package info.nightscout.androidaps.plugins.general.automation.triggers;

import android.app.Activity;
import android.content.Context;
import android.support.v4.app.FragmentManager;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.common.base.Optional;
import com.wdullaer.materialdatetimepicker.date.DatePickerDialog;
import com.wdullaer.materialdatetimepicker.time.TimePickerDialog;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.Date;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.JsonHelper;
import info.nightscout.androidaps.utils.T;

public class TriggerTime extends Trigger {

    private long runAt;
    private long lastRun;

    public TriggerTime() {
    }

    private TriggerTime(TriggerTime triggerTime) {
        super();
        lastRun = triggerTime.lastRun;
        runAt = triggerTime.runAt;
    }

    @Override
    public boolean shouldRun() {
        long now = DateUtil.now();
        if (now >= runAt && now - runAt < T.mins(5).msecs())
            return true;
        return false;
    }

    @Override
    public String toJSON() {
        JSONObject object = new JSONObject();
        JSONObject data = new JSONObject();
        try {
            data.put("runAt", runAt);
            data.put("lastRun", lastRun);
            object.put("type", TriggerTime.class.getName());
            object.put("data", data);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return object.toString();
    }

    @Override
    Trigger fromJSON(String data) {
        JSONObject o;
        try {
            o = new JSONObject(data);
            lastRun = JsonHelper.safeGetLong(o, "lastRun");
            runAt = JsonHelper.safeGetLong(o, "runAt");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return this;
    }

    @Override
    public int friendlyName() {
        return R.string.time;
    }

    @Override
    public String friendlyDescription() {
        return MainApp.gs(R.string.atspecifiedtime, DateUtil.dateAndTimeString(runAt));
    }

    @Override
    public Optional<Integer> icon() {
        return Optional.of(R.drawable.ic_access_alarm_24dp);
    }

    @Override
    void notifyAboutRun(long time) {
        lastRun = time;
    }

    TriggerTime runAt(long runAt) {
        this.runAt = runAt;
        return this;
    }

    @Override
    public Trigger duplicate() {
        return new TriggerTime(this);
    }

    public long getRunAt() {
        return runAt;
    }

    @Override
    public View createView(final Context context, FragmentManager fragmentManager) {
        LinearLayout root = (LinearLayout) super.createView(context, fragmentManager);
        //root.setOrientation(LinearLayout.HORIZONTAL);

        TextView dateButton = new TextView(context);
        TextView timeButton = new TextView(context);

        dateButton.setText(DateUtil.dateString(runAt));
        timeButton.setText(DateUtil.timeString(runAt));
        dateButton.setOnClickListener(view -> {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(runAt);
            DatePickerDialog dpd = DatePickerDialog.newInstance(
                    (view1, year, monthOfYear, dayOfMonth) -> {
                        Date eventTime = new Date(runAt);
                        eventTime.setYear(year - 1900);
                        eventTime.setMonth(monthOfYear);
                        eventTime.setDate(dayOfMonth);
                        runAt = eventTime.getTime();
                        dateButton.setText(DateUtil.dateString(runAt));
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
            );
            dpd.setThemeDark(true);
            dpd.dismissOnPause(true);
            android.app.FragmentManager fm = ((Activity) context).getFragmentManager();
            dpd.show(fm, "Datepickerdialog");
        });
        timeButton.setOnClickListener(view -> {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(runAt);
            TimePickerDialog tpd = TimePickerDialog.newInstance(
                    (view12, hourOfDay, minute, second) -> {
                        Date eventTime = new Date(runAt);
                        eventTime.setHours(hourOfDay);
                        eventTime.setMinutes(minute);
                        runAt = eventTime.getTime();
                        timeButton.setText(DateUtil.timeString(eventTime));
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    DateFormat.is24HourFormat(context)
            );
            tpd.setThemeDark(true);
            tpd.dismissOnPause(true);
            android.app.FragmentManager fm = ((Activity) context).getFragmentManager();
            tpd.show(fm, "Timepickerdialog");
        });

        root.addView(dateButton);
        root.addView(timeButton);
        return root;
    }
}
