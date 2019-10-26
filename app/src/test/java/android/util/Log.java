package android.util;

import java.time.LocalDateTime;

/**
 * Created by andy on 3/10/19.
 */

public class Log {

    // 03-10 13:44:42.847 12790-12888/info.nightscout.androidaps D/MedtronicHistoryData:

    static boolean isLoggingEnabled = false;


    public static void setLoggingEnabled(boolean enabled) {
        isLoggingEnabled = enabled;
    }


    private void writeLog(String type, String tag, String message) {
        if (isLoggingEnabled) {
            LocalDateTime ldt = LocalDateTime.now();
            System.out.println("DEBUG: " + tag + ": " + message);
        }
    }


    public static int d(String tag, String msg) {
        System.out.println("DEBUG: " + tag + ": " + msg);
        return 0;
    }


    public static int v(String tag, String msg) {
        System.out.println("VERBOSE: " + tag + ": " + msg);
        return 0;
    }


    public static int i(String tag, String msg) {
        System.out.println("INFO: " + tag + ": " + msg);
        return 0;
    }


    public static int w(String tag, String msg) {
        System.out.println("WARN: " + tag + ": " + msg);
        return 0;
    }


    public static int e(String tag, String msg) {
        System.out.println("ERROR: " + tag + ": " + msg);
        return 0;
    }

    // add other methods if required...
}
