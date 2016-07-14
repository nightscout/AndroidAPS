package info.nightscout.androidaps.plugins.SmsCommunicator.Receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.telephony.SmsMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainActivity;
import info.nightscout.androidaps.Services.DataService;
import info.nightscout.androidaps.plugins.SmsCommunicator.SmsCommunicatorFragment;

public class SmsReceiver extends WakefulBroadcastReceiver {
    private static Logger log = LoggerFactory.getLogger(SmsReceiver.class);

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Config.logFunctionCalls)
            log.debug("onReceive " + intent);
        startWakefulService(context, new Intent(context, DataService.class)
                .setAction(intent.getAction())
                .putExtras(intent));
    }
}