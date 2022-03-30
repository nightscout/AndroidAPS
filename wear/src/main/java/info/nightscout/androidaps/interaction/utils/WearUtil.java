package info.nightscout.androidaps.interaction.utils;

import android.content.Context;
import android.os.Bundle;
import android.os.PowerManager;

import com.google.android.gms.wearable.DataMap;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.shared.logging.AAPSLogger;
import info.nightscout.shared.logging.LTag;

/**
 * Created by andy on 3/5/19.
 * Adapted by dlvoy on 2019-11-06 using code from jamorham JoH class
 */

@Singleton
public class WearUtil {

    @Inject public Context context;
    @Inject public AAPSLogger aapsLogger;

    @Inject public WearUtil() {
    }

    private final boolean debug_wakelocks = false;
    private final Map<String, Long> rateLimits = new HashMap<>();
    private final String TAG = WearUtil.class.getName();

    //==============================================================================================
    // Time related util methods
    //==============================================================================================

    public String dateTimeText(long timeInMs) {
        Date d = new Date(timeInMs);
        return "" + d.getDay() + "." + d.getMonth() + "." + d.getYear() + " " + d.getHours() + ":" + d.getMinutes() + ":" + d.getSeconds();
    }

    public long timestamp() {
        return System.currentTimeMillis();
    }

    public long msSince(long when) {
        return (timestamp() - when);
    }

    public long msTill(long when) {
        return (when - timestamp());
    }

    //==============================================================================================
    // Thread and power management utils
    //==============================================================================================

    // return true if below rate limit
    public synchronized boolean isBelowRateLimit(String named, int onceForSeconds) {
        // check if over limit
        if ((rateLimits.containsKey(named)) && (timestamp() - rateLimits.get(named) < (onceForSeconds * 1000))) {
            aapsLogger.debug(LTag.WEAR, named + " rate limited to one for " + onceForSeconds + " seconds");
            return false;
        }
        // not over limit
        rateLimits.put(named, timestamp());
        return true;
    }

    public PowerManager.WakeLock getWakeLock(final String name, int millis) {
        final PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AAPS::" + name);
        wl.acquire(millis);
        if (debug_wakelocks)
            aapsLogger.debug(LTag.WEAR, "getWakeLock: " + name + " " + wl);
        return wl;
    }

    public void releaseWakeLock(PowerManager.WakeLock wl) {
        if (debug_wakelocks) aapsLogger.debug(LTag.WEAR, "releaseWakeLock: " + wl.toString());
        if (wl == null) return;
        if (wl.isHeld()) wl.release();
    }

    public void threadSleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            // we simply ignore if sleep was interrupted
        }
    }

    /**
     * Taken out to helper method to allow testing
     */
    public DataMap bundleToDataMap(Bundle bundle) {
        return DataMap.fromBundle(bundle);
    }
}
