package info.nightscout.androidaps.plugins.IobCobCalculator.events;

/**
 * Created by mike on 26.04.2017.
 */

public class EventNewHistoryData {
    public long time = 0;

    public EventNewHistoryData(long time) {
        this.time = time;
    }
}
