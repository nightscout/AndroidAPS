package info.nightscout.androidaps.events;

import android.os.Bundle;

/**
 * Event which is published with data fetched from NightScout specific for the
 * Food-class.
 *
 * Payload is the from NS retrieved JSON-String which should be handled by all
 * subscriber.
 */

public class EventNsFood extends Event {

    public static final int ADD = 0;
    public static final int UPDATE = 1;
    public static final int REMOVE = 2;

    private final int mode;

    private final Bundle payload;

    public EventNsFood(int mode, Bundle payload) {
        this.mode = mode;
        this.payload = payload;
    }

    public int getMode() {
        return mode;
    }

    public Bundle getPayload() {
        return payload;
    }
}
