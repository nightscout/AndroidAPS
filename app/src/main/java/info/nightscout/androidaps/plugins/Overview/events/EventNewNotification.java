package info.nightscout.androidaps.plugins.Overview.events;

import info.nightscout.androidaps.events.Event;
import info.nightscout.androidaps.plugins.Overview.notifications.Notification;

/**
 * Created by mike on 03.12.2016.
 */

public class EventNewNotification extends Event {
    public Notification notification;

    public EventNewNotification(Notification n) {
        notification = n;
    }
}
