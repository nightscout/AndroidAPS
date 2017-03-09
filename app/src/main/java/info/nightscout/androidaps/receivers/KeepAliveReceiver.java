package info.nightscout.androidaps.receivers;

/**
 * Created by mike on 07.07.2016.
 */

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.preference.PreferenceManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSProfile;

public class KeepAliveReceiver extends BroadcastReceiver {
    private static Logger log = LoggerFactory.getLogger(KeepAliveReceiver.class);

    @Override
    public void onReceive(Context context, Intent rIntent) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "");
        wl.acquire();


        final PumpInterface pump = MainApp.getConfigBuilder();
        final NSProfile profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();
        if (pump != null && profile != null && profile.getBasal(NSProfile.secondsFromMidnight()) != null) {
            boolean isBasalOutdated = false;
            boolean isStatusOutdated = false;

            Date lastConnection = pump.lastDataTime();
            if (lastConnection.getTime() + 30 * 60 * 1000L < new Date().getTime())
                isStatusOutdated = true;
            if (Math.abs(profile.getBasal(NSProfile.secondsFromMidnight()) - pump.getBaseBasalRate()) > pump.getPumpDescription().basalStep)
                isBasalOutdated = true;

            SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
            if (SP.getBoolean("syncprofiletopump", false) && !pump.isThisProfileSet(profile)) {
                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        pump.setNewBasalProfile(profile);
                    }
                });
                t.start();
            } else if (isStatusOutdated && !pump.isBusy()) {
                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        pump.refreshDataFromPump("KeepAlive. Status outdated.");
                    }
                });
                t.start();
            } else if (isBasalOutdated && !pump.isBusy()) {
                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        pump.refreshDataFromPump("KeepAlive. Basal outdated.");
                    }
                });
                t.start();
            }
        }

        log.debug("KeepAlive received");
        wl.release();
    }

    public void setAlarm(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(context, KeepAliveReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, i, 0);
        try {
            pi.send();
        } catch (PendingIntent.CanceledException e) {
        }
        am.cancel(pi);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), Constants.keepAliveMsecs, pi);
    }

    public void cancelAlarm(Context context) {
        Intent intent = new Intent(context, KeepAliveReceiver.class);
        PendingIntent sender = PendingIntent.getBroadcast(context, 0, intent, 0);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(sender);
    }
}