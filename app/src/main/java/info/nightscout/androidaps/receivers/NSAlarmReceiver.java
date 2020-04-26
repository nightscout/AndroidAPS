package info.nightscout.androidaps.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.general.nsclient.data.NSAlarm;
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.services.Intents;

public class NSAlarmReceiver extends BroadcastReceiver {
    private static Logger log = LoggerFactory.getLogger(L.CORE);

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null)
            return;
        Bundle bundle = intent.getExtras();
        String data = bundle.getString("data");
        JSONObject json = null;
        try {
            json = new JSONObject(data);
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
            return;
        }
        NSAlarm nsAlarm = new NSAlarm(json);
        switch (intent.getAction()) {
            case Intents.ACTION_ANNOUNCEMENT:
            case Intents.ACTION_ALARM:
            case Intents.ACTION_URGENT_ALARM:
                Notification notification = new Notification(nsAlarm);
                if (notification.isEnabled())
                    RxBus.INSTANCE.send(new EventNewNotification(notification));
                break;
            case Intents.ACTION_CLEAR_ALARM:
                RxBus.INSTANCE.send(new EventDismissNotification(Notification.NSALARM));
                RxBus.INSTANCE.send(new EventDismissNotification(Notification.NSURGENTALARM));
                break;
        }
    }
}
