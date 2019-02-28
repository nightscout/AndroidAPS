package info.nightscout.androidaps.plugins.aps.openAPSMA.events;

import info.nightscout.androidaps.events.EventUpdateGui;

/**
 * Created by mike on 05.08.2016.
 */
public class EventOpenAPSUpdateResultGui extends EventUpdateGui {
    public String text;

    public EventOpenAPSUpdateResultGui(String text) {
        this.text = text;
    }
}
