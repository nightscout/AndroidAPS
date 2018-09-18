package info.nightscout.androidaps.plugins.general.automation.triggers;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.JsonHelper;
import info.nightscout.utils.T;

public class TriggerTime extends Trigger {

    long lastRun;

    // Single execution
    long runAt;

    // Recurring
    boolean recurring;
    boolean monday = true;
    boolean tuesday = true;
    boolean wednesday = true;
    boolean thursday = true;
    boolean friday = true;
    boolean saturday = true;
    boolean sunday = true;
    int hour;
    int minute;

    long validTo;

    @Override
    boolean shouldRun() {
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

            if (monday && scheduledDayOfWeek == Calendar.MONDAY ||
                    tuesday && scheduledDayOfWeek == Calendar.TUESDAY ||
                    wednesday && scheduledDayOfWeek == Calendar.WEDNESDAY ||
                    thursday && scheduledDayOfWeek == Calendar.THURSDAY ||
                    friday && scheduledDayOfWeek == Calendar.FRIDAY ||
                    saturday && scheduledDayOfWeek == Calendar.SATURDAY ||
                    sunday && scheduledDayOfWeek == Calendar.SUNDAY) {
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
            data.put("monday", monday);
            data.put("tuesday", tuesday);
            data.put("wednesday", wednesday);
            data.put("thursday", thursday);
            data.put("friday", friday);
            data.put("saturday", saturday);
            data.put("sunday", sunday);
            data.put("hour", hour);
            data.put("minute", minute);
            data.put("validTo", validTo);
            object.put("type", TriggerTime.class.getName());
            object.put("data", data.toString());
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
            monday = JsonHelper.safeGetBoolean(o, "monday");
            tuesday = JsonHelper.safeGetBoolean(o, "tuesday");
            wednesday = JsonHelper.safeGetBoolean(o, "wednesday");
            thursday = JsonHelper.safeGetBoolean(o, "thursday");
            friday = JsonHelper.safeGetBoolean(o, "friday");
            saturday = JsonHelper.safeGetBoolean(o, "saturday");
            sunday = JsonHelper.safeGetBoolean(o, "sunday");
            hour = JsonHelper.safeGetInt(o, "hour");
            minute = JsonHelper.safeGetInt(o, "minute");
            validTo = JsonHelper.safeGetLong(o, "validTo");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return this;
    }

    @Override
    int friendlyName() {
        return R.string.time;
    }

    @Override
    String friendlyDescription() {
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

    TriggerTime monday(boolean monday) {
        this.monday = monday;
        return this;
    }

    TriggerTime tuesday(boolean tuesday) {
        this.tuesday = tuesday;
        return this;
    }

    TriggerTime wednesday(boolean wednesday) {
        this.wednesday = wednesday;
        return this;
    }

    TriggerTime thursday(boolean thursday) {
        this.thursday = thursday;
        return this;
    }

    TriggerTime friday(boolean friday) {
        this.friday = friday;
        return this;
    }

    TriggerTime saturday(boolean saturday) {
        this.saturday = saturday;
        return this;
    }

    TriggerTime sunday(boolean sunday) {
        this.sunday = sunday;
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

}
