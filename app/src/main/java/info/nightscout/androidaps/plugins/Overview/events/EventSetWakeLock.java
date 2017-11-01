package info.nightscout.androidaps.plugins.Overview.events;

import info.nightscout.androidaps.events.Event;

/**
 * Created by mike on 02.07.2017.
 */

public class EventSetWakeLock extends Event {
    public boolean lock = false;

    public EventSetWakeLock(boolean val) {
        lock = val;
    }
}
