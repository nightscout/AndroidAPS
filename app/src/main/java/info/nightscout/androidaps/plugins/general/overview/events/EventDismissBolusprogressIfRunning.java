package info.nightscout.androidaps.plugins.general.overview.events;

import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.events.Event;

/**
 * Created by adrian on 20/02/17.
 */

public class EventDismissBolusprogressIfRunning extends Event {
    public final PumpEnactResult result;

    public EventDismissBolusprogressIfRunning(PumpEnactResult result) {
        this.result = result;
    }
}
