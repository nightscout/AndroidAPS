package info.nightscout.androidaps.plugins.SmsCommunicator.events;

import android.os.Bundle;

import info.nightscout.androidaps.events.Event;

/**
 * Created by mike on 13.07.2016.
 */
public class EventNewSMS extends Event {
    public Bundle bundle;
    public EventNewSMS(Bundle bundle) {
        this.bundle = bundle;
    }
}
