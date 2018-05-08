package info.nightscout.androidaps.startupwizard.events;

import info.nightscout.androidaps.events.Event;

public class EventSWUpdate extends Event {
    public boolean redraw = false;

    public EventSWUpdate() {
    }

    public EventSWUpdate(boolean redraw) {
        this.redraw = redraw;
    }
}
