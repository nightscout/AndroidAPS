package info.nightscout.androidaps.plugins.SmsCommunicator.events;

import android.os.Bundle;

/**
 * Created by mike on 13.07.2016.
 */
public class EventNewSMS {
    public Bundle bundle;
    public EventNewSMS(Bundle bundle) {
        this.bundle = bundle;
    }
}
