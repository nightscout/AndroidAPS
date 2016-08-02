package info.nightscout.androidaps.receivers;

/**
 * Created by mike on 07.07.2016.
 */

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.PowerManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainActivity;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.plugins.DanaR.DanaRFragment;
import info.nightscout.androidaps.plugins.DanaR.Services.ExecutionService;
import info.nightscout.utils.ToastUtils;

public class KeepAliveReceiver extends BroadcastReceiver {
    private static Logger log = LoggerFactory.getLogger(KeepAliveReceiver.class);

    @Override
    public void onReceive(Context context, Intent rIntent) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "");
        wl.acquire();

        log.debug("KeepAlive received");
        final DanaRFragment danaRFragment = (DanaRFragment) MainApp.getSpecificPlugin(DanaRFragment.class);
        if (Config.DANAR && danaRFragment.isEnabled(PluginBase.PUMP)) {
            if (danaRFragment.getDanaRPump().lastConnection.getTime() + 30 * 60 * 1000L < new Date().getTime() && !danaRFragment.isConnected() && !danaRFragment.isConnecting()) {
                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        danaRFragment.doConnect("KeepAlive"); // TODO: only if if last conn > 30 min
                    }
                });
                t.start();
            }
        }

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