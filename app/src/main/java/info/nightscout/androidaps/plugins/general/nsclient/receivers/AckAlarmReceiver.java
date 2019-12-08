package info.nightscout.androidaps.plugins.general.nsclient.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.PowerManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.general.nsclient.NSClientPlugin;
import info.nightscout.androidaps.plugins.general.nsclient.data.AlarmAck;
import info.nightscout.androidaps.plugins.general.nsclient.services.NSClientService;
import info.nightscout.androidaps.utils.SP;

public class AckAlarmReceiver extends BroadcastReceiver {
    private static Logger log = LoggerFactory.getLogger(L.NSCLIENT);


    @Override
    public void onReceive(Context context, Intent intent) {
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                AckAlarmReceiver.class.getSimpleName());
        NSClientPlugin nsClientPlugin = NSClientPlugin.getPlugin();
        if (!nsClientPlugin.isEnabled(PluginType.GENERAL)) {
            return;
        }
        if (SP.getBoolean(R.string.key_ns_noupload, false)) {
            if (L.isEnabled(L.NSCLIENT))
                log.debug("Upload disabled. Message dropped");
            return;
        }
        wakeLock.acquire();
        try {
            Bundle bundles = intent.getExtras();
            if (bundles == null) return;
            if (!bundles.containsKey("level")) return;
            if (!bundles.containsKey("group")) return;
            if (!bundles.containsKey("silenceTime")) return;

            AlarmAck ack = new AlarmAck();
            ack.level = bundles.getInt("level");
            ack.group = bundles.getString("group");
            ack.silenceTime = bundles.getLong("silenceTime");

            NSClientService nsClientService = nsClientPlugin.nsClientService;
            if (nsClientService != null)
                nsClientService.sendAlarmAck(ack);

        } finally {
            wakeLock.release();
        }
    }
}
