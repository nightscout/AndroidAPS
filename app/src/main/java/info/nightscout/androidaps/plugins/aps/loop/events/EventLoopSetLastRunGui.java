package info.nightscout.androidaps.plugins.aps.loop.events;

import info.nightscout.androidaps.events.EventUpdateGui;

/**
 * Created by mike on 05.08.2016.
 */
public class EventLoopSetLastRunGui extends EventUpdateGui {
    public String text = null;

    public EventLoopSetLastRunGui(String text) {
        this.text = text;
    }
}
