package info.nightscout.androidaps.plugins.Overview.events;

import info.nightscout.androidaps.data.PumpEnactResult;

/**
 * Created by adrian on 20/02/17.
 */

public class EventDismissBolusprogressIfRunning {
    public final PumpEnactResult result;

    public EventDismissBolusprogressIfRunning(PumpEnactResult result) {
        this.result = result;
    }
}
