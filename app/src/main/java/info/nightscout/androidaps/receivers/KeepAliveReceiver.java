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

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.events.EventProfileSwitchChange;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.queue.commands.Command;
import info.nightscout.utils.LocalAlertUtils;

public class KeepAliveReceiver extends BroadcastReceiver {
    private static Logger log = LoggerFactory.getLogger(KeepAliveReceiver.class);
    public static final long STATUS_UPDATE_FREQUENCY = 15 * 60 * 1000L;

    public static void cancelAlarm(Context context) {
        Intent intent = new Intent(context, KeepAliveReceiver.class);
        PendingIntent sender = PendingIntent.getBroadcast(context, 0, intent, 0);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(sender);
    }

    @Override
    public void onReceive(Context context, Intent rIntent) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "");
        wl.acquire();

        LocalAlertUtils.shortenSnoozeInterval();
        LocalAlertUtils.checkStaleBGAlert();
        checkPump();

        log.debug("KeepAlive received");
        wl.release();
    }

    private void checkPump() {
        final PumpInterface pump = ConfigBuilderPlugin.getActivePump();
        final Profile profile = MainApp.getConfigBuilder().getProfile();
        if (pump != null && profile != null) {
            Date lastConnection = pump.lastDataTime();
            boolean isStatusOutdated = lastConnection.getTime() + STATUS_UPDATE_FREQUENCY < System.currentTimeMillis();
            boolean isBasalOutdated = Math.abs(profile.getBasal() - pump.getBaseBasalRate()) > pump.getPumpDescription().basalStep;

            LocalAlertUtils.checkPumpUnreachableAlarm(lastConnection, isStatusOutdated);

            if (!pump.isThisProfileSet(profile) && !ConfigBuilderPlugin.getCommandQueue().isRunning(Command.CommandType.BASALPROFILE)) {
                MainApp.bus().post(new EventProfileSwitchChange());
            } else if (isStatusOutdated && !pump.isBusy()) {
                ConfigBuilderPlugin.getCommandQueue().readStatus("KeepAlive. Status outdated.", null);
            } else if (isBasalOutdated && !pump.isBusy()) {
                ConfigBuilderPlugin.getCommandQueue().readStatus("KeepAlive. Basal outdated.", null);
            }
        }
    }

    //called by MainApp at first app start
    public void setAlarm(Context context) {

        LocalAlertUtils.shortenSnoozeInterval();
        LocalAlertUtils.presnoozeAlarms();

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

}
