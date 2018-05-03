package info.nightscout.androidaps.plugins.Overview.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.plugins.Treatments.Treatment;
import info.nightscout.androidaps.events.Event;

public class EventOverviewBolusProgress extends Event {
    private static Logger log = LoggerFactory.getLogger(EventOverviewBolusProgress.class);
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
