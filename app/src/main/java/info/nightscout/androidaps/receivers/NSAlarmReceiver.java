package info.nightscout.androidaps.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.Services.Intents;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSAlarm;
import info.nightscout.androidaps.plugins.Overview.notifications.Notification;
import info.nightscout.androidaps.plugins.Overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;

public class NSAlarmReceiver extends BroadcastReceiver {
    private static Logger log = LoggerFactory.getLogger(NSAlarmReceiver.class);

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
                    MainApp.bus().post(new EventNewNotification(notification));
                break;
            case Intents.ACTION_CLEAR_ALARM:
                MainApp.bus().post(new EventDismissNotification(Notification.NSALARM));
                MainApp.bus().post(new EventDismissNotification(Notification.NSURGENTALARM));
                break;
        }
    }
}
