package info.nightscout.androidaps.plugins.general.automation.triggers;

import android.content.Context;
import android.support.annotation.StringRes;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.dpro.widgets.WeekdaysPicker;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.JsonHelper;
import info.nightscout.utils.T;

public class TriggerTime extends Trigger {

    public enum DayOfWeek {
        MONDAY,
        TUESDAY,
        WEDNESDAY,
        THURSDAY,
        FRIDAY,
        SATURDAY,
        SUNDAY;

        private static final int[] calendarInts = new int[] {
                Calendar.MONDAY,
                Calendar.TUESDAY,
                Calendar.WEDNESDAY,
                Calendar.THURSDAY,
                Calendar.FRIDAY,
                Calendar.SATURDAY,
                Calendar.SUNDAY
        };

        private static final int[] fullNames = new int[] {
                R.string.weekday_monday,
                R.string.weekday_tuesday,
                R.string.weekday_wednesday,
                R.string.weekday_thursday,
                R.string.weekday_friday,
                R.string.weekday_saturday,
                R.string.weekday_sunday
        };

        private static final int[] shortNames = new int[] {
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
            for(int i = 0; i < calendarInts.length; ++i) {
                if (calendarInts[i] == day)
                    return values()[i];
            }
            return null;
        }

        public @StringRes int getFullName() {
            return fullNames[ordinal()];
        }

        public @StringRes int getShortName() {
            return shortNames[ordinal()];
        }
    }

    private final boolean[] weekdays = new boolean[DayOfWeek.values().length];

    private long lastRun;

    // Single execution
    private long runAt;

    // Recurring
    private boolean recurring;
    private int hour;
    private int minute;

    private long validTo;

    public TriggerTime() {
        super();
        setAll(false);
    }

    private TriggerTime(TriggerTime triggerTime) {
        super();
        lastRun = triggerTime.lastRun;
        runAt = triggerTime.runAt;
        recurring = triggerTime.recurring;
        hour = triggerTime.hour;
        minute = triggerTime.minute;
        validTo = triggerTime.validTo;

        for(int i = 0; i < weekdays.length; ++i) {
            weekdays[i] = triggerTime.weekdays[i];
        }
    }

    public void setAll(boolean value) {
        for(DayOfWeek day : DayOfWeek.values()) {
            set(day, value);
        }
    }

    public TriggerTime set(DayOfWeek day, boolean value) {
        weekdays[day.ordinal()] = value;
        return this;
    }

    public boolean isSet(DayOfWeek day) {
        return weekdays[day.ordinal()];
    }

    @Override
    public boolean shouldRun() {
        if (recurring) {
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
        } else {
            long now = DateUtil.now();
            if (now >= runAt && now - runAt < T.mins(5).msecs())
                return true;
            return false;
        }
    }

    @Override
    String toJSON() {
        JSONObject object = new JSONObject();
        JSONObject data = new JSONObject();
        try {
            data.put("lastRun", lastRun);
            data.put("runAt", runAt);
            data.put("recurring", recurring);
            for(int i = 0; i < weekdays.length; ++i) {
                data.put(DayOfWeek.values()[i].name(), weekdays[i]);
            }
            data.put("hour", hour);
            data.put("minute", minute);
            data.put("validTo", validTo);
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
            recurring = JsonHelper.safeGetBoolean(o, "recurring");
            for(int i = 0; i < weekdays.length; ++i) {
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
        return R.string.time;
    }

    @Override
    public String friendlyDescription() {
        if (recurring) {
            // TODO
            return "Every ";
        } else {
            return MainApp.gs(R.string.atspecifiedtime, DateUtil.dateAndTimeString(runAt));
        }
    }

    @Override
    void notifyAboutRun(long time) {
        lastRun = time;
    }

    @Override
    public Trigger duplicate() {
        return new TriggerTime(this);
    }

    TriggerTime lastRun(long lastRun) {
        this.lastRun = lastRun;
        return this;
    }

    TriggerTime runAt(long runAt) {
        this.runAt = runAt;
        return this;
    }

    TriggerTime recurring(boolean recurring) {
        this.recurring = recurring;
        return this;
    }

    TriggerTime validTo(long validTo) {
        this.validTo = validTo;
        return this;
    }

    TriggerTime hour(int hour) {
        this.hour = hour;
        return this;
    }

    TriggerTime minute(int minute) {
        this.minute = minute;
        return this;
    }

    private List<Integer> getSelectedDays() {
        List<Integer> selectedDays = new ArrayList<>();
        for(int i = 0; i < weekdays.length; ++i) {
            DayOfWeek day = DayOfWeek.values()[i];
            boolean selected = weekdays[i];
            if (selected) selectedDays.add(day.toCalendarInt());
        }
        return selectedDays;
    }

    @Override
    public View createView(Context context) {
        LinearLayout root = (LinearLayout) super.createView(context);

        // TODO: Replace external tool WeekdaysPicker with a self-made GUI element
        WeekdaysPicker weekdaysPicker = new WeekdaysPicker(context);
        weekdaysPicker.setEditable(true);
        weekdaysPicker.setSelectedDays(getSelectedDays());
        weekdaysPicker.setOnWeekdaysChangeListener((view, i, list) -> set(DayOfWeek.fromCalendarInt(i), list.contains(i)));
        weekdaysPicker.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(weekdaysPicker);
        return root;
    }
}
