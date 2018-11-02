package info.nightscout.androidaps.setupwizard.events;

import info.nightscout.androidaps.events.Event;

public class EventSWLabel extends Event {
    public String label;

    public EventSWLabel(String label) {
        this.label = label;
    }
}
