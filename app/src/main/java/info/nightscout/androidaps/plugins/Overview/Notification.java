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

    public int id;
    Date date;
    String text;
    Date validTo = new Date(0);
    int level;
}
