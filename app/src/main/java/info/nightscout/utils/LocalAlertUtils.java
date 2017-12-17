package info.nightscout.utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.Date;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.Overview.notifications.Notification;
import info.nightscout.androidaps.receivers.KeepAliveReceiver;

/**
 * Created by adrian on 17/12/17.
 */

public class LocalAlertUtils {
    public static int missedReadingsThreshold() {
        return SP.getInt(MainApp.sResources.getString(R.string.key_missed_bg_readings_threshold), 30) * 60 * 1000;
    }

    private static int pumpUnreachableThreshold() {
        return SP.getInt(MainApp.sResources.getString(R.string.key_pump_unreachable_threshold), 30) * 60 * 1000;
    }

    public static void checkPumpUnreachableAlarm(Date lastConnection, boolean isStatusOutdated) {
        boolean alarmTimeoutExpired = lastConnection.getTime() + pumpUnreachableThreshold() < System.currentTimeMillis();
        boolean nextAlarmOccurrenceReached = SP.getLong("nextPumpDisconnectedAlarm", 0l) < System.currentTimeMillis();

        if (Config.APS && SP.getBoolean(MainApp.sResources.getString(R.string.key_enable_pump_unreachable_alert), true)
                && isStatusOutdated && alarmTimeoutExpired && nextAlarmOccurrenceReached && !ConfigBuilderPlugin.getActiveLoop().isDisconnected()) {
            Notification n = new Notification(Notification.PUMP_UNREACHABLE, MainApp.sResources.getString(R.string.pump_unreachable), Notification.URGENT);
            n.soundId = R.raw.alarm;
            SP.putLong("nextPumpDisconnectedAlarm", System.currentTimeMillis() + pumpUnreachableThreshold());
            MainApp.bus().post(new EventNewNotification(n));
        }
    }

    /*Presnoozes the alarms with 5 minutes if no snooze exists.
         * Call only at startup!
         */
    public static void presnoozeAlarms() {
        if (SP.getLong("nextMissedReadingsAlarm", 0l) < System.currentTimeMillis()) {
            SP.putLong("nextMissedReadingsAlarm", System.currentTimeMillis() + 5 * 60 * 1000);
        }
        if (SP.getLong("nextPumpDisconnectedAlarm", 0l) < System.currentTimeMillis()) {
            SP.putLong("nextPumpDisconnectedAlarm", System.currentTimeMillis() + 5 * 60 * 1000);
        }
    }

    public static void shortenSnoozeInterval() {
        //shortens alarm times in case of setting changes or future data
        long nextMissedReadingsAlarm = SP.getLong("nextMissedReadingsAlarm", 0L);
        nextMissedReadingsAlarm = Math.min(System.currentTimeMillis() + missedReadingsThreshold(), nextMissedReadingsAlarm);
        SP.putLong("nextMissedReadingsAlarm", nextMissedReadingsAlarm);

        long nextPumpDisconnectedAlarm = SP.getLong("nextPumpDisconnectedAlarm", 0L);
        nextPumpDisconnectedAlarm = Math.min(System.currentTimeMillis() + pumpUnreachableThreshold(), nextPumpDisconnectedAlarm);
        SP.putLong("nextPumpDisconnectedAlarm", nextPumpDisconnectedAlarm);
    }

    public static void notifyPumpStatusRead(){
        //TODO: persist the actual time the pump is read and simplify the whole logic when to alarm

        final PumpInterface pump = ConfigBuilderPlugin.getActivePump();
        final Profile profile = MainApp.getConfigBuilder().getProfile();
        if (pump != null && profile != null && profile.getBasal() != null) {
            Date lastConnection = pump.lastDataTime();
            long earliestAlarmTime = lastConnection.getTime() + pumpUnreachableThreshold();
            if (SP.getLong("nextPumpDisconnectedAlarm", 0l) < earliestAlarmTime) {
                SP.putLong("nextPumpDisconnectedAlarm", earliestAlarmTime);
            }
        }
    }

    public static void checkStaleBGAlert() {
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
}
