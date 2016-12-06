package info.nightscout.androidaps.plugins.Overview.events;

import info.nightscout.androidaps.plugins.Overview.Notification;

/**
 * Created by mike on 03.12.2016.
 */

public class EventNewNotification {
    public Notification notification;

    public EventNewNotification(Notification n) {
        notification = n;
    }
}
