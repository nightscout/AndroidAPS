package info.nightscout.androidaps.plugins.PumpInsight.history;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.PumpInsight.utils.Helpers;

/**
 * Created by jamorham on 27/01/2018.
 *
 * In memory status storage class
 */

public class LiveHistory {

    private static String status = "";
    private static long status_time = -1;

    public static String getStatus() {
        if (status.equals("")) return status;
        return status + " " + Helpers.niceTimeScalar(Helpers.msSince(status_time)) + " " + MainApp.gs(R.string.ago);
    }

    public static long getStatusTime() {
        return status_time;
    }

    static void setStatus(String mystatus, long eventtime) {
        if (eventtime > status_time) {
            status_time = eventtime;
            status = mystatus;
        }
    }
}
