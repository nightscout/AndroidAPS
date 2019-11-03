package info.nightscout.androidaps.plugins.general.nsclient.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import info.nightscout.androidaps.plugins.general.persistentNotification.DummyService;

public class AutoStartReceiver extends BroadcastReceiver {
    public AutoStartReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            context.startForegroundService(new Intent(context, DummyService.class));
        else
            context.startService(new Intent(context, DummyService.class));
    }
}
