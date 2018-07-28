package info.nightscout.androidaps.receivers;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.services.DataService;
import info.nightscout.androidaps.Config;

public class DataReceiver extends WakefulBroadcastReceiver {
    private static Logger log = LoggerFactory.getLogger(Constants.DATASERVICE);

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Config.logDataService)
            log.debug("onReceive " + intent);
        startWakefulService(context, new Intent(context, DataService.class)
                .setAction(intent.getAction())
                .putExtras(intent));
    }
}
