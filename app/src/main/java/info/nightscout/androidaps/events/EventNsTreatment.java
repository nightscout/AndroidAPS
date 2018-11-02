package info.nightscout.androidaps.events;

import org.json.JSONObject;


/**
 * Event which is published with data fetched from NightScout specific for the
 * Treatment-class.
 * <p>
 * Payload is the from NS retrieved JSON-String which should be handled by all
 * subscriber.
 */

public class EventNsTreatment extends Event {

    public static final int ADD = 0;
    public static final int UPDATE = 1;
    public static final int REMOVE = 2;

    private final int mode;

    private final JSONObject payload;

    public EventNsTreatment(int mode, JSONObject payload) {
        this.mode = mode;
        this.payload = payload;
    }

    public int getMode() {
        return mode;
    }

    public JSONObject getPayload() {
        return payload;
    }
}
