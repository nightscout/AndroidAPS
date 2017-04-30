package info.nightscout.androidaps.plugins.NSClientInternal.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import info.nightscout.androidaps.plugins.NSClientInternal.services.NSClientService;

public class AutoStartReceiver extends BroadcastReceiver {
    public AutoStartReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        context.startService(new Intent(context, NSClientService.class));
    }
}
