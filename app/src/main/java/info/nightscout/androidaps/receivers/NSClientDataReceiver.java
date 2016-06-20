package info.nightscout.androidaps.receivers;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainActivity;
import info.nightscout.androidaps.Services.DataService;
import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.plugins.SourceNSClient.SourceNSClientFragment;
import info.nightscout.androidaps.plugins.SourceXdrip.SourceXdripFragment;

public class NSClientDataReceiver extends WakefulBroadcastReceiver {
    private static Logger log = LoggerFactory.getLogger(NSClientDataReceiver.class);

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Config.logFunctionCalls)
            log.debug("onReceive " + intent);
        startWakefulService(context, new Intent(context, DataService.class)
                .setAction(intent.getAction())
                .putExtras(intent));
    }
}
