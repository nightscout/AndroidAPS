package info.nightscout.androidaps.plugins.PumpDanaR.events;

import info.nightscout.androidaps.events.Event;

/**
 * Created by mike on 20.07.2016.
 */
public class EventDanaRSyncStatus extends Event {
    public String message;

    public EventDanaRSyncStatus() {
    }

    EventDanaRSyncStatus(String message) {
        this.message = message;
    }
}
