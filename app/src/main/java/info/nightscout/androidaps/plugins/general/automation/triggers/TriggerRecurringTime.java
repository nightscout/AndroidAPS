package info.nightscout.androidaps.plugins.general.automation.triggers;

import android.graphics.Typeface;
import android.text.format.DateFormat;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

import com.dpro.widgets.WeekdaysPicker;
import com.google.common.base.Optional;
import com.wdullaer.materialdatetimepicker.time.TimePickerDialog;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Objects;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.JsonHelper;
import info.nightscout.androidaps.utils.T;

public class TriggerRecurringTime extends Trigger {
    private static Logger log = LoggerFactory.getLogger(L.AUTOMATION);

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

        @Nullable
        public static DayOfWeek fromCalendarInt(int day) {
            for (int i = 0; i < calendarInts.length; ++i) {
                if (calendarInts[i] == day)
                    return values()[i];
            }
            return null;
        }

        public @StringRes
        int getShortName() {
            return shortNames[ordinal()];
        }
    }

    private final boolean[] weekdays = new boolean[DayOfWeek.values().length];

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

        if (weekdays.length >= 0)
            System.arraycopy(triggerTime.weekdays, 0, weekdays, 0, weekdays.length);
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

    private boolean isSet(DayOfWeek day) {
        return weekdays[day.ordinal()];
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

        if (isSet(Objects.requireNonNull(DayOfWeek.fromCalendarInt(scheduledDayOfWeek)))) {
            if (DateUtil.now() >= scheduled && DateUtil.now() - scheduled < T.mins(5).msecs()) {
                if (lastRun < scheduled) {
                    if (L.isEnabled(L.AUTOMATION))
                        log.debug("Ready for execution: " + friendlyDescription());
                    return true;
                }
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
            log.error("Unhandled exception", e);
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
            log.error("Unhandled exception", e);
        }
        return this;
    }

    @Override
    public int friendlyName() {
        return R.string.recurringTime;
    }

    @Override
    public String friendlyDescription() {
        int counter = 0;
        StringBuilder sb = new StringBuilder();
        sb.append(MainApp.gs(R.string.every));
        sb.append(" ");
        for (Integer i : getSelectedDays()) {
            if (counter > 0)
                sb.append(",");
            sb.append(MainApp.gs(Objects.requireNonNull(DayOfWeek.fromCalendarInt(i)).getShortName()));
            counter++;
        }
        sb.append(" ");

        Calendar scheduledCal = DateUtil.gregorianCalendar();
        scheduledCal.set(Calendar.HOUR_OF_DAY, hour);
        scheduledCal.set(Calendar.MINUTE, minute);
        scheduledCal.set(Calendar.SECOND, 0);
        long scheduled = scheduledCal.getTimeInMillis();

        sb.append(DateUtil.timeString(scheduled));

        if (counter == 0)
            return MainApp.gs(R.string.never);
        return sb.toString();
    }

    @Override
    public Optional<Integer> icon() {
        return Optional.of(R.drawable.ic_access_alarm_24dp);
    }

    @Override
    public Trigger duplicate() {
        return new TriggerRecurringTime(this);
    }

    TriggerRecurringTime lastRun(long lastRun) {
        this.lastRun = lastRun;
        return this;
    }

    @SuppressWarnings("SameParameterValue")
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
    public void generateDialog(LinearLayout root, FragmentManager fragmentManager) {
        TextView label = new TextView(root.getContext());

        // TODO: Replace external tool WeekdaysPicker with a self-made GUI element
        WeekdaysPicker weekdaysPicker = new WeekdaysPicker(root.getContext());
        weekdaysPicker.setEditable(true);
        weekdaysPicker.setSelectedDays(getSelectedDays());
        weekdaysPicker.setOnWeekdaysChangeListener((view, i, list) -> set(DayOfWeek.fromCalendarInt(i), list.contains(i)));
        weekdaysPicker.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        weekdaysPicker.setSundayFirstDay(Calendar.getInstance().getFirstDayOfWeek() == Calendar.SUNDAY);
        weekdaysPicker.redrawDays();

        root.addView(weekdaysPicker);

        TextView timeButton = new TextView(root.getContext());

        GregorianCalendar runAt = new GregorianCalendar();
        //Date runAt = new Date();
        runAt.set(Calendar.HOUR_OF_DAY, hour);
        runAt.set(Calendar.MINUTE, minute);
        timeButton.setText(DateUtil.timeString(runAt.getTimeInMillis()));
        timeButton.setOnClickListener(view -> {
            TimePickerDialog tpd = TimePickerDialog.newInstance(
                    (view12, hourOfDay, minute, second) -> {
                        hour(hourOfDay);
                        minute(minute);
                        runAt.set(Calendar.HOUR_OF_DAY, hour);
                        runAt.set(Calendar.MINUTE, minute);
                        timeButton.setText(DateUtil.timeString(runAt.getTimeInMillis()));
                    },
                    runAt.get(Calendar.HOUR_OF_DAY),
                    runAt.get(Calendar.MINUTE),
                    DateFormat.is24HourFormat(root.getContext())
            );
            tpd.setThemeDark(true);
            tpd.dismissOnPause(true);
            AppCompatActivity a = scanForActivity(root.getContext());
            if (a != null)
                tpd.show(a.getSupportFragmentManager(), "TimePickerDialog");
        });

        int px = MainApp.dpToPx(10);
        label.setText(MainApp.gs(R.string.atspecifiedtime, ""));
        label.setTypeface(label.getTypeface(), Typeface.BOLD);
        label.setPadding(px, px, px, px);
        timeButton.setPadding(px, px, px, px);

        LinearLayout l = new LinearLayout(root.getContext());
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        l.addView(label);
        l.addView(timeButton);
        root.addView(l);
    }

}
