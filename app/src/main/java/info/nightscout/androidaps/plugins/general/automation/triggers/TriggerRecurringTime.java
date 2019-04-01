package info.nightscout.androidaps.plugins.general.automation.triggers;

import android.app.Activity;
import android.content.Context;
import android.support.annotation.StringRes;
import android.support.v4.app.FragmentManager;
import android.text.format.DateFormat;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dpro.widgets.WeekdaysPicker;
import com.google.common.base.Optional;
import com.wdullaer.materialdatetimepicker.time.TimePickerDialog;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.JsonHelper;
import info.nightscout.androidaps.utils.T;

public class TriggerRecurringTime extends Trigger {

    public enum DayOfWeek {
        MONDAY,
        TUESDAY,
        WEDNESDAY,
        THURSDAY,
        FRIDAY,
        SATURDAY,
        SUNDAY;

        private static final int[] calendarInts = new int[]{
                Calendar.MONDAY,
                Calendar.TUESDAY,
                Calendar.WEDNESDAY,
                Calendar.THURSDAY,
                Calendar.FRIDAY,
                Calendar.SATURDAY,
                Calendar.SUNDAY
        };

        private static final int[] fullNames = new int[]{
                R.string.weekday_monday,
                R.string.weekday_tuesday,
                R.string.weekday_wednesday,
                R.string.weekday_thursday,
                R.string.weekday_friday,
                R.string.weekday_saturday,
                R.string.weekday_sunday
        };

        private static final int[] shortNames = new int[]{
                R.string.weekday_monday_short,
                R.string.weekday_tuesday_short,
                R.string.weekday_wednesday_short,
                R.string.weekday_thursday_short,
                R.string.weekday_friday_short,
                R.string.weekday_saturday_short,
                R.string.weekday_sunday_short
        };

        public int toCalendarInt() {
            return calendarInts[ordinal()];
        }

        public static DayOfWeek fromCalendarInt(int day) {
            for (int i = 0; i < calendarInts.length; ++i) {
                if (calendarInts[i] == day)
                    return values()[i];
            }
            return null;
        }

        public @StringRes
        int getFullName() {
            return fullNames[ordinal()];
        }

        public @StringRes
        int getShortName() {
            return shortNames[ordinal()];
        }
    }

    private final boolean[] weekdays = new boolean[DayOfWeek.values().length];

    private long lastRun;

    // Recurring
    private int hour;
    private int minute;

    private long validTo;

    public TriggerRecurringTime() {
        super();
        setAll(false);
    }

    private TriggerRecurringTime(TriggerRecurringTime triggerTime) {
        super();
        lastRun = triggerTime.lastRun;
        hour = triggerTime.hour;
        minute = triggerTime.minute;
        validTo = triggerTime.validTo;

        for (int i = 0; i < weekdays.length; ++i) {
            weekdays[i] = triggerTime.weekdays[i];
        }
    }

    public void setAll(boolean value) {
        for (DayOfWeek day : DayOfWeek.values()) {
            set(day, value);
        }
    }

    public TriggerRecurringTime set(DayOfWeek day, boolean value) {
        weekdays[day.ordinal()] = value;
        return this;
    }

    public boolean isSet(DayOfWeek day) {
        return weekdays[day.ordinal()];
    }

    public long getLastRun() {
        return lastRun;
    }

    @Override
    public boolean shouldRun() {
        if (validTo != 0 && DateUtil.now() > validTo)
            return false;
        Calendar c = Calendar.getInstance();
        int scheduledDayOfWeek = c.get(Calendar.DAY_OF_WEEK);

        Calendar scheduledCal = DateUtil.gregorianCalendar();
        scheduledCal.set(Calendar.HOUR_OF_DAY, hour);
        scheduledCal.set(Calendar.MINUTE, minute);
        scheduledCal.set(Calendar.SECOND, 0);
        long scheduled = scheduledCal.getTimeInMillis();

        if (isSet(DayOfWeek.fromCalendarInt(scheduledDayOfWeek))) {
            if (DateUtil.now() >= scheduled && DateUtil.now() - scheduled < T.mins(5).msecs()) {
                if (lastRun < scheduled)
                    return true;
            }
        }
        return false;
    }

    @Override
    public String toJSON() {
        JSONObject object = new JSONObject();
        JSONObject data = new JSONObject();
        try {
            data.put("lastRun", lastRun);
            for (int i = 0; i < weekdays.length; ++i) {
                data.put(DayOfWeek.values()[i].name(), weekdays[i]);
            }
            data.put("hour", hour);
            data.put("minute", minute);
            data.put("validTo", validTo);
            object.put("type", TriggerRecurringTime.class.getName());
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
            for (int i = 0; i < weekdays.length; ++i) {
                weekdays[i] = JsonHelper.safeGetBoolean(o, DayOfWeek.values()[i].name());
            }
            hour = JsonHelper.safeGetInt(o, "hour");
            minute = JsonHelper.safeGetInt(o, "minute");
            validTo = JsonHelper.safeGetLong(o, "validTo");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return this;
    }

    @Override
    public int friendlyName() {
        return R.string.recurringTime;
    }

    @Override
    public String friendlyDescription() {
        // TODO
        return "Every ";
    }

    @Override
    public Optional<Integer> icon() {
        return Optional.of(R.drawable.ic_access_alarm_24dp);
    }

    @Override
    void notifyAboutRun(long time) {
        lastRun = time;
    }

    @Override
    public Trigger duplicate() {
        return new TriggerRecurringTime(this);
    }

    TriggerRecurringTime lastRun(long lastRun) {
        this.lastRun = lastRun;
        return this;
    }

    TriggerRecurringTime validTo(long validTo) {
        this.validTo = validTo;
        return this;
    }

    TriggerRecurringTime hour(int hour) {
        this.hour = hour;
        return this;
    }

    TriggerRecurringTime minute(int minute) {
        this.minute = minute;
        return this;
    }

    private List<Integer> getSelectedDays() {
        List<Integer> selectedDays = new ArrayList<>();
        for (int i = 0; i < weekdays.length; ++i) {
            DayOfWeek day = DayOfWeek.values()[i];
            boolean selected = weekdays[i];
            if (selected) selectedDays.add(day.toCalendarInt());
        }
        return selectedDays;
    }

    @Override
    public View createView(final Context context, FragmentManager fragmentManager) {
        LinearLayout root = (LinearLayout) super.createView(context, fragmentManager);

        // TODO: Replace external tool WeekdaysPicker with a self-made GUI element
        WeekdaysPicker weekdaysPicker = new WeekdaysPicker(context);
        weekdaysPicker.setEditable(true);
        weekdaysPicker.setSelectedDays(getSelectedDays());
        weekdaysPicker.setOnWeekdaysChangeListener((view, i, list) -> set(DayOfWeek.fromCalendarInt(i), list.contains(i)));
        weekdaysPicker.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(weekdaysPicker);

        TextView dateButton = new TextView(context);
        TextView timeButton = new TextView(context);

        Date runAt = new Date();
        runAt.setHours(hour);
        runAt.setMinutes(minute);
        timeButton.setText(DateUtil.timeString(runAt));
        timeButton.setOnClickListener(view -> {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(runAt);
            TimePickerDialog tpd = TimePickerDialog.newInstance(
                    (view12, hourOfDay, minute, second) -> {
                        this.hour = hourOfDay;
                        this.minute = minute;
                        runAt.setHours(this.hour);
                        runAt.setMinutes(this.minute);
                        timeButton.setText(DateUtil.timeString(runAt));
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
