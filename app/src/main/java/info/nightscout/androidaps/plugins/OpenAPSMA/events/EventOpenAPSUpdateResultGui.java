package info.nightscout.androidaps.plugins.OpenAPSMA.events;

import info.nightscout.androidaps.events.EventUpdateGui;

/**
 * Created by mike on 05.08.2016.
 */
public class EventOpenAPSUpdateResultGui extends EventUpdateGui {
    public String text = null;

    public EventOpenAPSUpdateResultGui(String text) {
        this.text = text;
    }
}
