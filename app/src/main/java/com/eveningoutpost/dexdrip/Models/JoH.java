package com.eveningoutpost.dexdrip.Models;

import android.content.Context;
import android.os.PowerManager;
import android.util.Log;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.GregorianCalendar;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

public class JoH {

    private final static String TAG = "jamorham JoH";

    public static String dateTimeText(long timestamp) {
        return android.text.format.DateFormat.format("yyyy-MM-dd kk:mm:ss", timestamp).toString();
    }

    public static String niceTimeScalar(long t) {
        String unit = MainApp.gs(R.string.unit_second);
        t = t / 1000;
        if (t != 1) unit = MainApp.gs(R.string.unit_seconds);
        if (t > 59) {
            unit = MainApp.gs(R.string.unit_minute);
            t = t / 60;
            if (t != 1) unit = MainApp.gs(R.string.unit_minutes);
            if (t > 59) {
                unit = MainApp.gs(R.string.unit_hour);
                t = t / 60;
                if (t != 1) unit = MainApp.gs(R.string.unit_hours);
                if (t > 24) {
                    unit = MainApp.gs(R.string.unit_day);
                    t = t / 24;
                    if (t != 1) unit = MainApp.gs(R.string.unit_days);
                    if (t > 28) {
                        unit = MainApp.gs(R.string.unit_week);
                        t = t / 7;
                        if (t != 1) unit = MainApp.gs(R.string.unit_weeks);
                    }
                }
            }
        }
        //if (t != 1) unit = unit + "s"; //implemented plurality in every step, because in other languages plurality of time is not every time adding the same character
        return qs((double) t, 0) + " " + unit;
    }

    // singletons to avoid repeated allocation
    private static DecimalFormatSymbols dfs;
    private static DecimalFormat df;
    public static String qs(double x, int digits) {

        if (digits == -1) {
            digits = 0;
            if (((int) x != x)) {
                digits++;
                if ((((int) x * 10) / 10 != x)) {
                    digits++;
                    if ((((int) x * 100) / 100 != x)) digits++;
                }
            }
        }

        if (dfs == null) {
            final DecimalFormatSymbols local_dfs = new DecimalFormatSymbols();
            local_dfs.setDecimalSeparator('.');
            dfs = local_dfs; // avoid race condition
        }

        final DecimalFormat this_df;
        // use singleton if on ui thread otherwise allocate new as DecimalFormat is not thread safe
        if (Thread.currentThread().getId() == 1) {
            if (df == null) {
                final DecimalFormat local_df = new DecimalFormat("#", dfs);
                local_df.setMinimumIntegerDigits(1);
                df = local_df; // avoid race condition
            }
            this_df = df;
        } else {
            this_df = new DecimalFormat("#", dfs);
        }

        this_df.setMaximumFractionDigits(digits);
        return this_df.format(x);
    }

    public static long getTimeZoneOffsetMs() {
        return new GregorianCalendar().getTimeZone().getRawOffset();
    }

    public static boolean emptyString(final String str) {
        return str == null || str.length() == 0;
    }

    public static PowerManager.WakeLock getWakeLock(final String name, int millis) {
        final PowerManager pm = (PowerManager) MainApp.instance().getSystemService(Context.POWER_SERVICE);
        final PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, name);
        wl.acquire(millis);
        Log.d(TAG, "getWakeLock: " + name + " " + wl.toString());
        return wl;
    }

    public static void releaseWakeLock(PowerManager.WakeLock wl) {
        Log.d(TAG, "releaseWakeLock: " + wl.toString());
        if (wl == null) return;
        if (wl.isHeld()) {
            try {
                wl.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing wakelock: " + e);
            }
        }
    }

    public static PowerManager.WakeLock fullWakeLock(final String name, long millis) {
        final PowerManager pm = (PowerManager) MainApp.instance().getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, name);
        wl.acquire(millis);
        Log.d(TAG, "fullWakeLock: " + name + " " + wl.toString());
        return wl;
    }


}
