package info.nightscout.androidaps.receivers;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;

public class TimeDateOrTZChangeReceiver extends BroadcastReceiver {

    private static Logger LOG = LoggerFactory.getLogger(L.PUMP);

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();

        PumpInterface activePump = ConfigBuilderPlugin.getPlugin().getActivePump();

        LOG.debug("Date, Time and/or TimeZone changed.");

        if (action != null && activePump != null) {
            LOG.debug("Date, Time and/or TimeZone changed. Notifying pump driver.");
            activePump.timeDateOrTimeZoneChanged();
        }
    }


    public void registerBroadcasts(MainApp mainApp) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_DATE_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        mainApp.registerReceiver(this, filter);
    }


}