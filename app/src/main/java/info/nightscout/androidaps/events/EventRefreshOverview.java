package info.nightscout.androidaps.events;

/**
 * Created by mike on 16.06.2017.
 */

public class EventRefreshOverview extends Event {
    public String from;

    public EventRefreshOverview(String from) {
        this.from = from;
    }
}
