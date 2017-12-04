package info.nightscout.androidaps.receivers;

/**
 * Created by mike on 07.07.2016.
 */

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Overview.notifications.Notification;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.utils.SP;

public class KeepAliveReceiver extends BroadcastReceiver {
    private static Logger log = LoggerFactory.getLogger(KeepAliveReceiver.class);
    public static final long STATUS_UPDATE_FREQUENCY = 15 * 60 * 1000L;

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

        shortenSnoozeInterval();
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
        final PumpInterface pump = ConfigBuilderPlugin.getActivePump();
        final Profile profile = MainApp.getConfigBuilder().getProfile();
        if (pump != null && profile != null && profile.getBasal() != null) {
            Date lastConnection = pump.lastDataTime();
            boolean isStatusOutdated = lastConnection.getTime() + STATUS_UPDATE_FREQUENCY < System.currentTimeMillis();
            boolean isBasalOutdated = Math.abs(profile.getBasal() - pump.getBaseBasalRate()) > pump.getPumpDescription().basalStep;

            boolean alarmTimeoutExpired = lastConnection.getTime() + pumpUnreachableThreshold() < System.currentTimeMillis();
            boolean nextAlarmOccurrenceReached = SP.getLong("nextPumpDisconnectedAlarm", 0l) < System.currentTimeMillis();

            if (Config.APS && SP.getBoolean(MainApp.sResources.getString(R.string.key_enable_pump_unreachable_alert), true)
                    && isStatusOutdated && alarmTimeoutExpired && nextAlarmOccurrenceReached && !ConfigBuilderPlugin.getActiveLoop().isDisconnected()) {
                Notification n = new Notification(Notification.PUMP_UNREACHABLE, MainApp.sResources.getString(R.string.pump_unreachable), Notification.URGENT);
                n.soundId = R.raw.alarm;
                SP.putLong("nextPumpDisconnectedAlarm", System.currentTimeMillis() + pumpUnreachableThreshold());
                MainApp.bus().post(new EventNewNotification(n));
            }

            if (SP.getBoolean("syncprofiletopump", false) && !pump.isThisProfileSet(profile)) {
                MainApp.getConfigBuilder().getCommandQueue().setProfile(profile, null);
            } else if (isStatusOutdated && !pump.isBusy()) {
                MainApp.getConfigBuilder().getCommandQueue().readStatus("KeepAlive. Status outdated.", null);
            } else if (isBasalOutdated && !pump.isBusy()) {
                MainApp.getConfigBuilder().getCommandQueue().readStatus("KeepAlive. Basal outdated.", null);
            }
        }
    }

    //called by MainApp at first app start
    public void setAlarm(Context context) {

        shortenSnoozeInterval();
        presnoozeAlarms();

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

    /*Presnoozes the alarms with 5 minutes if no snooze exists.
     * Call only at startup!
     */
    public void presnoozeAlarms() {
        if (SP.getLong("nextMissedReadingsAlarm", 0l) < System.currentTimeMillis()) {
            SP.putLong("nextMissedReadingsAlarm", System.currentTimeMillis() + 5 * 60 * 1000);
        }
        if (SP.getLong("nextPumpDisconnectedAlarm", 0l) < System.currentTimeMillis()) {
            SP.putLong("nextPumpDisconnectedAlarm", System.currentTimeMillis() + 5 * 60 * 1000);
        }
    }

    public void cancelAlarm(Context context) {
        Intent intent = new Intent(context, KeepAliveReceiver.class);
        PendingIntent sender = PendingIntent.getBroadcast(context, 0, intent, 0);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(sender);
    }

    static void shortenSnoozeInterval() {
        //shortens alarm times in case of setting changes or future data
        long nextMissedReadingsAlarm = SP.getLong("nextMissedReadingsAlarm", 0L);
        nextMissedReadingsAlarm = Math.min(System.currentTimeMillis() + missedReadingsThreshold(), nextMissedReadingsAlarm);
        SP.putLong("nextMissedReadingsAlarm", nextMissedReadingsAlarm);

        long nextPumpDisconnectedAlarm = SP.getLong("nextPumpDisconnectedAlarm", 0L);
        nextPumpDisconnectedAlarm = Math.min(System.currentTimeMillis() + pumpUnreachableThreshold(), nextPumpDisconnectedAlarm);
        SP.putLong("nextPumpDisconnectedAlarm", nextPumpDisconnectedAlarm);
    }
}
