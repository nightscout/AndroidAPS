package info.nightscout.androidaps.plugins.Overview;

import java.util.Date;

/**
 * Created by mike on 03.12.2016.
 */

public class Notification {
    public static final int URGENT = 0;
    public static final int NORMAL = 1;
    public static final int LOW = 2;
    public static final int INFO = 3;

    public static final int PROFILE_SET_FAILED = 0;
    public static final int PROFILE_SET_OK = 1;
    public static final int EASYMODE_ENABLED = 2;
    public static final int EXTENDED_BOLUS_DISABLED = 3;
    public static final int UD_MODE_ENABLED = 4;
    public static final int PROFILE_NOT_SET_NOT_INITIALIZED = 5;
    public static final int FAILED_UDPATE_PROFILE = 6;
    public static final int BASAL_VALUE_BELOW_MINIMUM = 7;
    public static final int OLD_NSCLIENT = 8;

    public int id;
    public Date date;
    public String text;
    public int level;
    public Date validTo = new Date(0);

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
        this.validTo = new Date(new Date().getTime() + validMinutes * 60 * 1000L);
    }

    public Notification(int id, String text, int level) {
        this.id = id;
        this.date = new Date();
        this.text = text;
        this.level = level;
        this.validTo = new Date(0);
    }
}
