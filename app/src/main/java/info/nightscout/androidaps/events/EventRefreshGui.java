package info.nightscout.androidaps.events;

/**
 * Created by mike on 13.06.2016.
 */
public class EventRefreshGui extends Event {
    public boolean recreate = false;
    public EventRefreshGui(boolean recreate) {
        this.recreate = recreate;
    }
    public EventRefreshGui(){
        this(false);
    }
}
