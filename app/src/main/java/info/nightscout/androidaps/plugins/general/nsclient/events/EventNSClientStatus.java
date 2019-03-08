package info.nightscout.androidaps.plugins.general.nsclient.events;

import info.nightscout.androidaps.events.Event;

/**
 * Created by mike on 02.01.2016.
 */
public class EventNSClientStatus extends Event {
    public String status = "";

    public EventNSClientStatus(String status) {
        this.status = status;
    }

    public EventNSClientStatus() {
    }

}
