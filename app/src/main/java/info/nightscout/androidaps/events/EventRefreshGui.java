package info.nightscout.androidaps.events;

/**
 * Created by mike on 13.06.2016.
 */
public class EventRefreshGui {

    public boolean isSwitchToLast() {
        return switchToLast;
    }

    private final boolean switchToLast;

    public EventRefreshGui(boolean switchToLast){
        this.switchToLast = switchToLast;
    }

}
