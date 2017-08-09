package info.nightscout.androidaps.plugins.Overview.events;

/**
 * Created by mike on 02.07.2017.
 */

public class EventSetWakeLock {
    public boolean lock = false;

    public EventSetWakeLock(boolean val) {
        lock = val;
    }
}
