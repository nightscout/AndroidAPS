package info.nightscout.androidaps.events;

import android.location.Location;

public class EventLocationChange extends Event {
    public Location location;

    public EventLocationChange(Location location) {
        this.location = location;
    }
}
