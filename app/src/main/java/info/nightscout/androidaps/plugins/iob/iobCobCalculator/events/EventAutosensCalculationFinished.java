package info.nightscout.androidaps.plugins.iob.iobCobCalculator.events;

import info.nightscout.androidaps.events.Event;
import info.nightscout.androidaps.events.EventLoop;

/**
 * Created by mike on 30.04.2017.
 */

public class EventAutosensCalculationFinished extends EventLoop {
    public Event cause;

    public EventAutosensCalculationFinished(Event cause) {
        this.cause = cause;
    }
}
