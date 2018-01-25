package info.nightscout.androidaps.plugins.PumpInsight.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

import info.nightscout.androidaps.plugins.PumpInsight.connector.Connector;

/**
 * Created by jamorham on 24/01/2018.
 *
 * Useful utility methods from xDrip+
 *
 */

public class Helpers {

    private static final String TAG = "InsightHelpers";

    private static final Map<String, Long> rateLimits = new HashMap<>();

    // return true if below rate limit
    public static synchronized boolean ratelimit(String name, int seconds) {
        // check if over limit
        if ((rateLimits.containsKey(name)) && (tsl() - rateLimits.get(name) < (seconds * 1000))) {
            Log.d(TAG, name + " rate limited: " + seconds + " seconds");
            return false;
        }
        // not over limit
        rateLimits.put(name, tsl());
        return true;
    }

    public static long tsl() {
        return System.currentTimeMillis();
    }

    public static long msSince(long when) {
        return (tsl() - when);
    }

    public static long msTill(long when) {
        return (when - tsl());
    }

    public static boolean checkPackageExists(Context context, String TAG, String packageName) {
        try {
            final PackageManager pm = context.getPackageManager();
            final PackageInfo pi = pm.getPackageInfo(packageName, 0);
            return pi.packageName.equals(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        } catch (Exception e) {
            Log.wtf(TAG, "Exception trying to determine packages! " + e);
            return false;
        }
    }
}
