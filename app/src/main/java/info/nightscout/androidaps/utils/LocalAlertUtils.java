package info.nightscout.androidaps.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;

/**
 * Created by adrian on 17/12/17.
 */

public class LocalAlertUtils {
    private static Logger log = LoggerFactory.getLogger(LocalAlertUtils.class);

    public static long missedReadingsThreshold() {
        return T.mins(SP.getInt(MainApp.gs(R.string.key_missed_bg_readings_threshold), 30)).msecs();
    }

    private static long pumpUnreachableThreshold() {
        return T.mins(SP.getInt(MainApp.gs(R.string.key_pump_unreachable_threshold), 30)).msecs();
    }

    public static void checkPumpUnreachableAlarm(long lastConnection, boolean isStatusOutdated) {
        boolean alarmTimeoutExpired = lastConnection + pumpUnreachableThreshold() < System.currentTimeMillis();
        boolean nextAlarmOccurrenceReached = SP.getLong("nextPumpDisconnectedAlarm", 0L) < System.currentTimeMillis();

        if (Config.APS && SP.getBoolean(MainApp.gs(R.string.key_enable_pump_unreachable_alert), true)
                && isStatusOutdated && alarmTimeoutExpired && nextAlarmOccurrenceReached && !LoopPlugin.getPlugin().isDisconnected()) {
            log.debug("Generating pump unreachable alarm. lastConnection: " + DateUtil.dateAndTimeString(lastConnection) + " isStatusOutdated: " + isStatusOutdated);
            Notification n = new Notification(Notification.PUMP_UNREACHABLE, MainApp.gs(R.string.pump_unreachable), Notification.URGENT);
            n.soundId = R.raw.alarm;
            SP.putLong("nextPumpDisconnectedAlarm", System.currentTimeMillis() + pumpUnreachableThreshold());
            RxBus.INSTANCE.send(new EventNewNotification(n));
            if (SP.getBoolean(R.string.key_ns_create_announcements_from_errors, true)) {
                NSUpload.uploadError(n.text);
            }
        }
        if (!isStatusOutdated && !alarmTimeoutExpired)
            RxBus.INSTANCE.send(new EventDismissNotification(Notification.PUMP_UNREACHABLE));
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

    public static void notifyPumpStatusRead() {
        //TODO: persist the actual time the pump is read and simplify the whole logic when to alarm

        final PumpInterface pump = ConfigBuilderPlugin.getPlugin().getActivePump();
        final Profile profile = ProfileFunctions.getInstance().getProfile();
        if (pump != null && profile != null) {
            long lastConnection = pump.lastDataTime();
            long earliestAlarmTime = lastConnection + pumpUnreachableThreshold();
            if (SP.getLong("nextPumpDisconnectedAlarm", 0l) < earliestAlarmTime) {
                SP.putLong("nextPumpDisconnectedAlarm", earliestAlarmTime);
            }
        }
    }

    public static void checkStaleBGAlert() {
        BgReading bgReading = DatabaseHelper.lastBg();
        if (SP.getBoolean(MainApp.gs(R.string.key_enable_missed_bg_readings_alert), false)
                && bgReading != null && bgReading.date + missedReadingsThreshold() < System.currentTimeMillis()
                && SP.getLong("nextMissedReadingsAlarm", 0l) < System.currentTimeMillis()) {
            Notification n = new Notification(Notification.BG_READINGS_MISSED, MainApp.gs(R.string.missed_bg_readings), Notification.URGENT);
            n.soundId = R.raw.alarm;
            SP.putLong("nextMissedReadingsAlarm", System.currentTimeMillis() + missedReadingsThreshold());
            RxBus.INSTANCE.send(new EventNewNotification(n));
            if (SP.getBoolean(R.string.key_ns_create_announcements_from_errors, true)) {
                NSUpload.uploadError(n.text);
            }
        }
    }
}
