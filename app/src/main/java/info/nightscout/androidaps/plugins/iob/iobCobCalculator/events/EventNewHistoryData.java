package info.nightscout.androidaps.plugins.iob.iobCobCalculator.events;

import info.nightscout.androidaps.events.Event;

/**
 * Created by mike on 26.04.2017.
 */

public class EventNewHistoryData extends Event {
    public long time = 0;

    public EventNewHistoryData(long time) {
        this.time = time;
    }
}
