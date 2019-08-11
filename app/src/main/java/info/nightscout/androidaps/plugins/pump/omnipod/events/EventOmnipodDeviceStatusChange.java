package info.nightscout.androidaps.plugins.pump.omnipod.events;

import info.nightscout.androidaps.events.Event;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkError;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.PumpDeviceState;

/**
 * Created by andy on 4.8.2019
 */
public class EventOmnipodDeviceStatusChange extends Event {

    public RileyLinkServiceState rileyLinkServiceState;
    public RileyLinkError rileyLinkError;

    public PumpDeviceState pumpDeviceState;
    public String errorDescription;


    public EventOmnipodDeviceStatusChange(RileyLinkServiceState rileyLinkServiceState) {
        this(rileyLinkServiceState, null);
    }


    public EventOmnipodDeviceStatusChange(RileyLinkServiceState rileyLinkServiceState, RileyLinkError rileyLinkError) {
        this.rileyLinkServiceState = rileyLinkServiceState;
        this.rileyLinkError = rileyLinkError;
    }


    public EventOmnipodDeviceStatusChange(PumpDeviceState pumpDeviceState) {
        this.pumpDeviceState = pumpDeviceState;
    }


    public EventOmnipodDeviceStatusChange(PumpDeviceState pumpDeviceState, String errorDescription) {
        this.pumpDeviceState = pumpDeviceState;
        this.errorDescription = errorDescription;
    }


    @Override
    public String toString() {
        return "EventOmnipodDeviceStatusChange [" + "rileyLinkServiceState=" + rileyLinkServiceState
                + ", rileyLinkError=" + rileyLinkError + ", pumpDeviceState=" + pumpDeviceState + ']';
    }
}
