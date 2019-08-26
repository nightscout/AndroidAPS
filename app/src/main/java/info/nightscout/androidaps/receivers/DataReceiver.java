package info.nightscout.androidaps.receivers;

import android.content.Context;
import android.content.Intent;
import androidx.legacy.content.WakefulBroadcastReceiver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.services.DataService;

public class DataReceiver extends WakefulBroadcastReceiver {
    private static Logger log = LoggerFactory.getLogger(L.DATASERVICE);

    @Override
    public void onReceive(Context context, Intent intent) {
        if (L.isEnabled(L.DATASERVICE))
            log.debug("onReceive " + intent);
        startWakefulService(context, new Intent(context, DataService.class)
                .setAction(intent.getAction())
                .putExtras(intent));
    }
}
