package info.nightscout.androidaps.plugins.Overview;

import java.util.Date;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSAlarm;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSSettingsStatus;
import info.nightscout.utils.SP;

/**
 * Created by mike on 03.12.2016.
 */

public class Notification {
    public static final int URGENT = 0;
    public static final int NORMAL = 1;
    public static final int LOW = 2;
    public static final int INFO = 3;
    public static final int ANNOUNCEMENT = 4;

    public static final int PROFILE_SET_FAILED = 0;
    public static final int PROFILE_SET_OK = 1;
    public static final int EASYMODE_ENABLED = 2;
    public static final int EXTENDED_BOLUS_DISABLED = 3;
    public static final int UD_MODE_ENABLED = 4;
    public static final int PROFILE_NOT_SET_NOT_INITIALIZED = 5;
    public static final int FAILED_UDPATE_PROFILE = 6;
    public static final int BASAL_VALUE_BELOW_MINIMUM = 7;
    public static final int OLD_NSCLIENT = 8;
    public static final int OLD_NS = 9;
    public static final int INVALID_PHONE_NUMBER = 10;
    public static final int APPROACHING_DAILY_LIMIT = 11;
    public static final int NSCLIENT_NO_WRITE_PERMISSION = 12;
    public static final int MISSING_SMS_PERMISSION = 13;
    public static final int ISF_MISSING = 14;
    public static final int IC_MISSING = 15;
    public static final int BASAL_MISSING = 16;
    public static final int TARGET_MISSING = 17;
    public static final int NSANNOUNCEMENT = 18;
    public static final int NSALARM = 19;
    public static final int NSURGENTALARM = 20;

    public int id;
    public Date date;
    public String text;
    public int level;
    public Date validTo = new Date(0);

    public NSAlarm nsAlarm = null;
    public Integer soundId = null;

    public Notification() {
    }

    public Notification(int id, Date date, String text, int level, Date validTo) {
        this.id = id;
        this.date = date;
        this.text = text;
        this.level = level;
        this.validTo = validTo;
    }

    public Notification(int id, String text, int level, int validMinutes) {
        this.id = id;
        this.date = new Date();
        this.text = text;
        this.level = level;
        this.validTo = new Date(System.currentTimeMillis() + validMinutes * 60 * 1000L);
    }

    public Notification(int id, String text, int level) {
        this.id = id;
        this.date = new Date();
        this.text = text;
        this.level = level;
        this.validTo = new Date(0);
    }

    public Notification(NSAlarm nsAlarm) {
        this.date = new Date();
        this.validTo = new Date(0);
        this.nsAlarm = nsAlarm;
        switch (nsAlarm.getLevel()) {
            case 0:
                this.id = NSANNOUNCEMENT;
                this.level = ANNOUNCEMENT;
                this.text = nsAlarm.getMessage();
                this.validTo = new Date(System.currentTimeMillis() + 60 * 60 * 1000L);
                break;
            case 1:
                this.id = NSALARM;
                this.level = NORMAL;
                this.text = nsAlarm.getTile();
                if (isAlarmForLow() && SP.getBoolean(R.string.key_nsalarm_low, false) || isAlarmForHigh() && SP.getBoolean(R.string.key_nsalarm_high, false))
                    this.soundId = R.raw.alarm;
                break;
            case 2:
                this.id = NSURGENTALARM;
                this.level = URGENT;
                this.text = nsAlarm.getTile();
                if (isAlarmForLow() && SP.getBoolean(R.string.key_nsalarm_urgent_low, false) || isAlarmForHigh() && SP.getBoolean(R.string.key_nsalarm_urgent_high, false))
                    this.soundId = R.raw.urgentalarm;
                break;
        }
    }

    public boolean isEnabled() {
        if (nsAlarm == null)
            return true;
        if (level == ANNOUNCEMENT)
            return true;
        if (level == NORMAL && isAlarmForLow() && SP.getBoolean(R.string.key_nsalarm_low, false) || isAlarmForHigh() && SP.getBoolean(R.string.key_nsalarm_high, false))
            return true;
        if (level == URGENT && isAlarmForLow() && SP.getBoolean(R.string.key_nsalarm_urgent_low, false) || isAlarmForHigh() && SP.getBoolean(R.string.key_nsalarm_urgent_high, false))
            return true;
        return false;
    }

    boolean isAlarmForLow() {
        BgReading bgReading = MainApp.getDbHelper().lastBg();
        if (bgReading == null)
            return false;
        Double threshold = NSSettingsStatus.getInstance().getThreshold("bgTargetTop");
        if (threshold == null)
            return false;
        if (bgReading.value <= threshold)
            return true;
        return false;
    }

    boolean isAlarmForHigh() {
        BgReading bgReading = MainApp.getDbHelper().lastBg();
        if (bgReading == null)
            return false;
        Double threshold = NSSettingsStatus.getInstance().getThreshold("bgTargetBottom");
        if (threshold == null)
            return false;
        if (bgReading.value >= threshold)
            return true;
        return false;
    }
}
