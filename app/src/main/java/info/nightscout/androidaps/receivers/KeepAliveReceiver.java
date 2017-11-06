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
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.Overview.notifications.Notification;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.utils.SP;

public class KeepAliveReceiver extends BroadcastReceiver {
    public static final long STATUS_UPDATE_FREQUENCY = 15 * 60 * 1000L;
    private static Logger log = LoggerFactory.getLogger(KeepAliveReceiver.class);

    // TODO consider moving this into an Alarms plugin that works offline and can be configured
    // (e.g. override silent mode at night only)

    private static int missedReadingsThreshold() {
        return SP.getInt(MainApp.sResources.getString(R.string.key_missed_bg_readings_threshold), 30) * 60 * 1000;
    }

    private static int pumpUnreachableThreshold() {
        return SP.getInt(MainApp.sResources.getString(R.string.key_pump_unreachable_threshold), 30) * 60 * 1000;
    }

    @Override
    public void onReceive(Context context, Intent rIntent) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "");
        wl.acquire();

        initAlarmTimes();
        checkBg();
        checkPump();

        log.debug("KeepAlive received");
        wl.release();
    }

    private void checkBg() {
        BgReading bgReading = DatabaseHelper.lastBg();
        if (SP.getBoolean(MainApp.sResources.getString(R.string.key_enable_missed_bg_readings_alert), false)
        && bgReading != null && bgReading.date + missedReadingsThreshold() < System.currentTimeMillis()
                && SP.getLong("nextMissedReadingsAlarm", 0l) < System.currentTimeMillis()) {
            Notification n = new Notification(Notification.BG_READINGS_MISSED, MainApp.sResources.getString(R.string.missed_bg_readings), Notification.URGENT);
            n.soundId = R.raw.alarm;
            SP.putLong("nextMissedReadingsAlarm", System.currentTimeMillis() + missedReadingsThreshold());
            MainApp.bus().post(new EventNewNotification(n));
        }
    }

    private void checkPump() {
        final PumpInterface pump = MainApp.getConfigBuilder();
        final Profile profile = MainApp.getConfigBuilder().getProfile();
        if (pump != null && profile != null && profile.getBasal() != null) {
            Date lastConnection = pump.lastDataTime();

            boolean isStatusOutdated = lastConnection.getTime() + STATUS_UPDATE_FREQUENCY < System.currentTimeMillis();
            boolean isBasalOutdated = Math.abs(profile.getBasal() - pump.getBaseBasalRate()) > pump.getPumpDescription().basalStep;

            boolean alarmTimeoutExpired = lastConnection.getTime() + pumpUnreachableThreshold() < System.currentTimeMillis();
            boolean nextAlarmOccurrenceReached = SP.getLong("nextPumpDisconnectedAlarm", 0l) < System.currentTimeMillis();

            if (SP.getBoolean(MainApp.sResources.getString(R.string.key_enable_pump_unreachable_alert), true)
                    && isStatusOutdated && alarmTimeoutExpired && nextAlarmOccurrenceReached) {
                Notification n = new Notification(Notification.PUMP_UNREACHABLE, MainApp.sResources.getString(R.string.pump_unreachable), Notification.URGENT);
                n.soundId = R.raw.alarm;
                SP.putLong("nextPumpDisconnectedAlarm", System.currentTimeMillis() + pumpUnreachableThreshold());
                MainApp.bus().post(new EventNewNotification(n));
            } else if (SP.getBoolean("syncprofiletopump", false) && !pump.isThisProfileSet(profile)) {
                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        pump.setNewBasalProfile(profile);
                    }
                }, "pump-update-basal-profile");
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

    static void initAlarmTimes() {
        //shortens alarm times in case of setting changes or future data
        long nextMissedReadingsAlarm = SP.getLong("nextMissedReadingsAlarm", 0l);
        nextMissedReadingsAlarm = Math.min(System.currentTimeMillis() + missedReadingsThreshold(), nextMissedReadingsAlarm);
        SP.putLong("nextMissedReadingsAlarm", nextMissedReadingsAlarm);

        long nextPumpDisconnectedAlarm = SP.getLong("nextPumpDisconnectedAlarm", 0l);
        nextPumpDisconnectedAlarm = Math.min(System.currentTimeMillis() + missedReadingsThreshold(), nextPumpDisconnectedAlarm);
        SP.putLong("nextPumpDisconnectedAlarm", nextPumpDisconnectedAlarm);
    }

}