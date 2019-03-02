package info.nightscout.androidaps.plugins.general.overview.events;

import info.nightscout.androidaps.events.Event;

/**
 * Created by mike on 03.12.2016.
 */

public class EventDismissNotification extends Event {
    public int id;

    public EventDismissNotification(int did) {
        id = did;
    }

}
