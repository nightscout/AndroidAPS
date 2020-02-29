package info.nightscout.androidaps.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.TimeZone;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodUtil;
import info.nightscout.androidaps.utils.TimeChangeType;

public class TimeDateOrTZChangeReceiver extends BroadcastReceiver {

    private static Logger LOG = LoggerFactory.getLogger(L.PUMP);

    private boolean isDST;

    public TimeDateOrTZChangeReceiver() {
        isDST = calculateDST();
    }

    private boolean calculateDST() {
        TimeZone timeZone = TimeZone.getDefault();
        Date nowDate = new Date();

        if (timeZone.useDaylightTime()) {
            return (timeZone.inDaylightTime(nowDate));
        } else {
            return false;
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();

        PumpInterface activePump = ConfigBuilderPlugin.getPlugin().getActivePump();

        if (activePump==null) {
            LOG.debug("TimeDateOrTZChangeReceiver::Time and/or TimeZone changed. [action={}]. Pump is null, exiting.", action);
            return;
        }

        LOG.debug("TimeDateOrTZChangeReceiver::Date, Time and/or TimeZone changed. [action={}]", action);
        LOG.debug("TimeDateOrTZChangeReceiver::Intent::{}", OmnipodUtil.getGsonInstance().toJson(intent));

        if (action==null) {
            LOG.error("TimeDateOrTZChangeReceiver::Action is null. Exiting.");
        } else if (Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
            LOG.info("TimeDateOrTZChangeReceiver::Timezone changed. Notifying pump driver.");
            activePump.timezoneOrDSTChanged(TimeChangeType.TimezoneChange);
        } else if (Intent.ACTION_TIME_CHANGED.equals(action)) {
            boolean currentDst = calculateDST();

            if (currentDst==isDST) {
                LOG.info("TimeDateOrTZChangeReceiver::Time changed (manual). Notifying pump driver.");
                activePump.timezoneOrDSTChanged(TimeChangeType.ManualTimeChange);
            } else {
                if (currentDst) {
                    LOG.info("TimeDateOrTZChangeReceiver::DST started. Notifying pump driver.");
                    activePump.timezoneOrDSTChanged(TimeChangeType.DST_Started);
                } else {
                    LOG.info("TimeDateOrTZChangeReceiver::DST ended. Notifying pump driver.");
                    activePump.timezoneOrDSTChanged(TimeChangeType.DST_Ended);
                }
            }

            isDST = currentDst;
        } else {
            LOG.error("TimeDateOrTZChangeReceiver::Unknown action received [name={}]. Exiting.", action);
        }

    }


    public void registerBroadcasts(MainApp mainApp) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        //filter.addAction(Intent.ACTION_DATE_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        mainApp.registerReceiver(this, filter);
    }


}