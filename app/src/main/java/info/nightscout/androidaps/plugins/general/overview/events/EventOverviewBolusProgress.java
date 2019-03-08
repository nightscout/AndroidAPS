package info.nightscout.androidaps.plugins.general.overview.events;

import info.nightscout.androidaps.plugins.treatments.Treatment;
import info.nightscout.androidaps.events.Event;

public class EventOverviewBolusProgress extends Event {
    public String status = "";
    public Treatment t = null;
    public int percent = 0;
    public int bolusId;
    private static EventOverviewBolusProgress eventOverviewBolusProgress = null;

     public EventOverviewBolusProgress() {
    }

    public boolean isSMB(){
         return (t != null) && t.isSMB;
    }

    public static EventOverviewBolusProgress getInstance() {
        if(eventOverviewBolusProgress == null) {
            eventOverviewBolusProgress = new EventOverviewBolusProgress();
        }
        return eventOverviewBolusProgress;
    }

}
