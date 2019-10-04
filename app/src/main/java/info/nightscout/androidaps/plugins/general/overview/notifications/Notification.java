package info.nightscout.androidaps.plugins.general.overview.notifications;

import java.util.Date;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.plugins.general.nsclient.data.NSAlarm;
import info.nightscout.androidaps.plugins.general.nsclient.data.NSSettingsStatus;
import info.nightscout.androidaps.utils.SP;

// Added by Rumen for debugging

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
    public static final int INVALID_MESSAGE_BODY = 11;
    public static final int APPROACHING_DAILY_LIMIT = 12;
    public static final int NSCLIENT_NO_WRITE_PERMISSION = 13;
    public static final int MISSING_SMS_PERMISSION = 14;
    public static final int PUMPERROR = 15;
    public static final int WRONGSERIALNUMBER = 16;

    public static final int NSANNOUNCEMENT = 18;
    public static final int NSALARM = 19;
    public static final int NSURGENTALARM = 20;
    public static final int SHORT_DIA = 21;
    public static final int TOAST_ALARM = 22;
    public static final int WRONGBASALSTEP = 23;
    public static final int WRONG_DRIVER = 24;
    public static final int COMBO_PUMP_ALARM = 25;
    public static final int PUMP_UNREACHABLE = 26;
    public static final int BG_READINGS_MISSED = 27;
    public static final int UNSUPPORTED_FIRMWARE = 28;
    public static final int MINIMAL_BASAL_VALUE_REPLACED = 29;
    public static final int BASAL_PROFILE_NOT_ALIGNED_TO_HOURS = 30;
    public static final int ZERO_VALUE_IN_PROFILE = 31;
    public static final int PROFILE_SWITCH_MISSING = 32;
    public static final int NOT_ENG_MODE_OR_RELEASE = 33;
    public static final int WRONG_PUMP_PASSWORD = 34;
    public static final int PERMISSION_STORAGE = 35;
    public static final int PERMISSION_LOCATION = 36;
    public static final int PERMISSION_BATTERY = 37;
    public static final int PERMISSION_SMS = 38;
    public static final int MAXIMUM_BASAL_VALUE_REPLACED = 39;
    public static final int NSMALFUNCTION = 40;
    public static final int NEWVERSIONDETECTED = 41;
    public static final int SENDLOGFILES = 42;
    public static final int DEVICENOTPAIRED = 43;
    public static final int MEDTRONIC_PUMP_ALARM = 44;
    public static final int RILEYLINK_CONNECTION = 45;
    public static final int PERMISSION_PHONESTATE = 46;
    public static final int INSIGHT_DATE_TIME_UPDATED = 47;
    public static final int INSIGHT_TIMEOUT_DURING_HANDSHAKE = 48;
    public static final int DST_LOOP_DISABLED = 49;
    public static final int DST_IN_24H = 50;
    public static final int DISKFULL = 51;
    public static final int OLDVERSION = 52;
    public static final int USERMESSAGE = 53;
    public static final int OVER_24H_TIME_CHANGE_REQUESTED = 54;
    public static final int INVALID_VERSION = 55;


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

    public Notification(int id) {
        this.id = id;
        this.date = new Date();
        this.validTo = new Date(0);
    }

    public Notification text(String text) {
        this.text = text;
        return this;
    }

    public Notification level(int level) {
        this.level = level;
        return this;
    }

    public Notification sound(int soundId) {
        this.soundId = soundId;
        return this;
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
                if (isAlarmForLow() && SP.getBoolean(R.string.key_nsalarm_low, false) || isAlarmForHigh() && SP.getBoolean(R.string.key_nsalarm_high, false) || isAlarmForStaleData() && SP.getBoolean(R.string.key_nsalarm_staledata, false))
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
        if (level == NORMAL && isAlarmForLow() && SP.getBoolean(R.string.key_nsalarm_low, false) || isAlarmForHigh() && SP.getBoolean(R.string.key_nsalarm_high, false) || isAlarmForStaleData() && SP.getBoolean(R.string.key_nsalarm_staledata, false)) {
            return true;
        }
        if (level == URGENT && isAlarmForLow() && SP.getBoolean(R.string.key_nsalarm_urgent_low, false) || isAlarmForHigh() && SP.getBoolean(R.string.key_nsalarm_urgent_high, false) || isAlarmForStaleData() && SP.getBoolean(R.string.key_nsalarm_urgent_staledata, false))
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

    public static boolean isAlarmForStaleData() {
        long snoozedTo = SP.getLong("snoozedTo", 0L);
        if (snoozedTo != 0L) {
            if (System.currentTimeMillis() < SP.getLong("snoozedTo", 0L)) {
                //log.debug("Alarm is snoozed for next "+(SP.getLong("snoozedTo", 0L)-System.currentTimeMillis())/1000+" seconds");
                return false;
            }
        }
        BgReading bgReading = MainApp.getDbHelper().lastBg();
        if (bgReading == null)
            return false;
        long bgReadingAgo = System.currentTimeMillis() - bgReading.date;
        int bgReadingAgoMin = (int) (bgReadingAgo / (1000 * 60));
        // Added for testing
        // bgReadingAgoMin = 20;
        boolean openAPSEnabledAlerts = NSSettingsStatus.getInstance().openAPSEnabledAlerts();
        //log.debug("bgReadingAgoMin value is:"+bgReadingAgoMin);
        //log.debug("Stale alarm snoozed to: "+(System.currentTimeMillis() - snoozedTo)/60000L);
        Double threshold = NSSettingsStatus.getInstance().getThreshold("alarmTimeagoWarnMins");
        //log.debug("OpenAPS Alerts enabled: "+openAPSEnabledAlerts);
        // if no thresshold from Ns get it loccally
        if (threshold == null) threshold = SP.getDouble(R.string.key_nsalarm_staledatavalue, 15D);
        // No threshold of OpenAPS Alarm so using the one for BG
        // Added OpenAPSEnabledAlerts to alarm check
        if ((bgReadingAgoMin > threshold && SP.getBoolean(R.string.key_nsalarm_staledata, false)) || (bgReadingAgoMin > threshold && openAPSEnabledAlerts)) {
            return true;
        }
        //snoozing for threshold
        SP.putLong("snoozedTo", (long) (bgReading.date + (threshold * 1000 * 60L)));
        //log.debug("New bg data is available Alarm is snoozed for next "+threshold*1000*60+" seconds");
        return false;
    }
}
