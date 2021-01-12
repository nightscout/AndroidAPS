package info.nightscout.androidaps.plugins.general.overview.notifications;

import androidx.annotation.NonNull;

import info.nightscout.androidaps.utils.T;

public class Notification {
    // TODO join with NotificationWithAction after change to enums
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
    public static final int OVER_24H_TIME_CHANGE_REQUESTED = 54;
    public static final int INVALID_VERSION = 55;
    public static final int PERMISSION_SYSTEM_WINDOW = 56;
    public static final int TIME_OR_TIMEZONE_CHANGE = 58;
    public static final int OMNIPOD_POD_NOT_ATTACHED = 59;
    public static final int CARBS_REQUIRED = 60;
    public static final int OMNIPOD_POD_SUSPENDED = 61;
    public static final int OMNIPOD_POD_ALERTS_UPDATED = 62;
    public static final int OMNIPOD_POD_ALERTS = 63;
    public static final int OMNIPOD_TBR_ALERTS = 64;
    public static final int OMNIPOD_POD_FAULT = 66;
    public static final int OMNIPOD_UNCERTAIN_SMB = 67;
    public static final int OMNIPOD_UNKNOWN_TBR = 68;
    public static final int OMNIPOD_STARTUP_STATUS_REFRESH_FAILED = 69;
    public static final int OMNIPOD_TIME_OUT_OF_SYNC = 70;

    public static final int IMPORTANCE_HIGH = 2;

    public static final String CATEGORY_ALARM = "alarm";

    public static final int USERMESSAGE = 1000;

    public int id;
    public long date;
    public String text;
    public int level;
    public long validTo = 0;

    public Integer soundId = null;

    protected Runnable action = null;
    protected int buttonText = 0;

    public Notification() {
    }

    public Notification(int id, long date, String text, int level, long validTo) {
        this.id = id;
        this.date = date;
        this.text = text;
        this.level = level;
        this.validTo = validTo;
    }

    public Notification(int id, String text, int level, int validMinutes) {
        this.id = id;
        this.date = System.currentTimeMillis();
        this.text = text;
        this.level = level;
        this.validTo = System.currentTimeMillis() + T.mins(validMinutes).msecs();
    }

    public Notification(int id, @NonNull String text, int level) {
        this.id = id;
        this.date = System.currentTimeMillis();
        this.text = text;
        this.level = level;
    }

    public Notification(int id) {
        this.id = id;
        this.date = System.currentTimeMillis();
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
}
