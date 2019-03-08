package info.nightscout.androidaps.plugins.general.nsclient.receivers;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientRestart;
import info.nightscout.androidaps.plugins.general.nsclient.services.NSClientService;

public class RestartReceiver extends WakefulBroadcastReceiver {
    public RestartReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        startWakefulService(context, new Intent(context, NSClientService.class)
                .setAction(intent.getAction())
                .putExtras(intent));

        MainApp.bus().post(new EventNSClientRestart());
        completeWakefulIntent(intent);
    }
}
