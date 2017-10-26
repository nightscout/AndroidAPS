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
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.plugins.Overview.Notification;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;

public class KeepAliveReceiver extends BroadcastReceiver {
    private static Logger log = LoggerFactory.getLogger(KeepAliveReceiver.class);

    @Override
    public void onReceive(Context context, Intent rIntent) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "");
        wl.acquire();

        checkBg();
        checkPump();

        log.debug("KeepAlive received");
        wl.release();
    }

    private void checkBg() {
        BgReading bgReading = DatabaseHelper.lastBg();
        if (bgReading != null && bgReading.date + 25 * 60 * 1000 < System.currentTimeMillis()) {
            Notification n = new Notification(Notification.BG_READINGS_MISSED, "Missed BG readings", Notification.URGENT);
            n.soundId = R.raw.alarm;
            MainApp.bus().post(new EventNewNotification(n));
        }
    }

    private void checkPump() {
        final PumpInterface pump = MainApp.getConfigBuilder();
        final Profile profile = MainApp.getConfigBuilder().getProfile();
        if (pump != null && profile != null && profile.getBasal() != null) {
            Date lastConnection = pump.lastDataTime();

            boolean isStatusOutdated = lastConnection.getTime() + 15 * 60 * 1000L < System.currentTimeMillis();
            boolean isBasalOutdated = Math.abs(profile.getBasal() - pump.getBaseBasalRate()) > pump.getPumpDescription().basalStep;

            SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
            if (isStatusOutdated && lastConnection.getTime() + 25 * 60 * 1000 < System.currentTimeMillis()) {
                // TODO the alarm will trigger every 5m until the problem is resolved. That can get annoying quiet quickly if
                // fixing the problem takes longer (or is not immediately possible because the pump was forgotten)?
                // suppress this for another 25m if the message was dismissed?
                // The alarm sound is played back as regular media, that means it might be muted if sound level is at 0
                // a simple 'Enable/disable alarms' button on the actions tab?
                Notification n = new Notification(Notification.PUMP_UNREACHABLE, "Pump unreachable", Notification.URGENT);
                n.soundId = R.raw.alarm;
                MainApp.bus().post(new EventNewNotification(n));
            } else if (SP.getBoolean("syncprofiletopump", false) && !pump.isThisProfileSet(profile)) {
                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        pump.setNewBasalProfile(profile);
                    }
                }, "pump-refresh");
                t.start();
            } else if (isStatusOutdated && !pump.isBusy()) {
                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        pump.refreshDataFromPump("KeepAlive. Status outdated.");
                    }
                }, "pump-refresh");
                t.start();
            } else if (isBasalOutdated && !pump.isBusy()) {
                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        pump.refreshDataFromPump("KeepAlive. Basal outdated.");
                    }
                }, "pump-refresh");
                t.start();
            }
        }
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