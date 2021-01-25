package info.nightscout.androidaps.interaction.utils;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;

import com.google.android.gms.wearable.DataMap;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import info.nightscout.androidaps.Aaps;

/**
 * Created by andy on 3/5/19.
 * Adapted by dlvoy on 2019-11-06 using code from jamorham JoH class
 */

public class WearUtil {

    private final static boolean debug_wakelocks = false;
    private static final Map<String, Long> rateLimits = new HashMap<String, Long>();
    private static final String TAG = WearUtil.class.getName();

    //==============================================================================================
    // Time related util methods
    //==============================================================================================

    public static String dateTimeText(long timeInMs) {
        Date d = new Date(timeInMs);
        return "" + d.getDay() + "." + d.getMonth() + "." + d.getYear() + " " + d.getHours() + ":" + d.getMinutes() + ":" + d.getSeconds();
    }

    public static long timestamp() {
        return System.currentTimeMillis();
    }

    public static long msSince(long when) {
        return (timestamp() - when);
    }

    public static long msTill(long when) {
        return (when - timestamp());
    }

    //==============================================================================================
    // Thread and power management utils
    //==============================================================================================

    // return true if below rate limit
    public static synchronized boolean isBelowRateLimit(String named, int onceForSeconds) {
        // check if over limit
        if ((rateLimits.containsKey(named)) && (timestamp() - rateLimits.get(named) < (onceForSeconds * 1000))) {
            Log.d(TAG, named + " rate limited to one for " + onceForSeconds + " seconds");
            return false;
        }
        // not over limit
        rateLimits.put(named, timestamp());
        return true;
    }

    public static PowerManager.WakeLock getWakeLock(final String name, int millis) {
        final PowerManager pm = (PowerManager) Aaps.getAppContext().getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AAPS::"+name);
        wl.acquire(millis);
        if (debug_wakelocks) Log.d(TAG, "getWakeLock: " + name + " " + wl.toString());
        return wl;
    }

    public static void releaseWakeLock(PowerManager.WakeLock wl) {
        if (debug_wakelocks) Log.d(TAG, "releaseWakeLock: " + wl.toString());
        if (wl == null) return;
        if (wl.isHeld()) wl.release();
    }

    public static void startActivity(Class c) {
        Aaps.getAppContext().startActivity(getStartActivityIntent(c));
    }

    public static Intent getStartActivityIntent(Class c) {
        return new Intent(Aaps.getAppContext(), c).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    public static void threadSleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            // we simply ignore if sleep was interrupted
        }
    }

    public static String joinSet(Set<String> set, String separator) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (String item : set) {
            final String itemToAdd = item.trim();
            if (itemToAdd.length() > 0) {
                if (i > 0) {
                    sb.append(separator);
                }
                i++;
                sb.append(itemToAdd);
            }
        }
        return sb.toString();
    }

    public static Set<String> explodeSet(String joined, String separator) {
        // special RegEx literal \\Q starts sequence we escape, \\E ends is
        // we use it to escape separator for use in RegEx
        String[] items = joined.split("\\Q"+separator+"\\E");
        Set<String> set = new HashSet<>();
        for (String item : items) {
            final String itemToAdd = item.trim();
            if (itemToAdd.length() > 0) {
                set.add(itemToAdd);
            }
        }
        return set;
    }

    /**
     * Taken out to helper method to allow testing
     */
    public static DataMap bundleToDataMap(Bundle bundle) {
        return DataMap.fromBundle(bundle);
    }
}
