package info.nightscout.androidaps.plugins.pump.medtronic.events;

import info.nightscout.androidaps.events.Event;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkError;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.PumpDeviceState;

/**
 * Created by andy on 04.06.2018.
 */
public class EventMedtronicDeviceStatusChange extends Event {

    public RileyLinkServiceState rileyLinkServiceState;
    public RileyLinkError rileyLinkError;

    public PumpDeviceState pumpDeviceState;
    public String errorDescription;


    // public EventMedtronicDeviceStatusChange(RileyLinkServiceState rileyLinkServiceState, PumpDeviceState
    // pumpDeviceState) {
    // this.rileyLinkServiceState = rileyLinkServiceState;
    // this.pumpDeviceState = pumpDeviceState;
    // }

    public EventMedtronicDeviceStatusChange(RileyLinkServiceState rileyLinkServiceState) {
        this(rileyLinkServiceState, null);
    }


    public EventMedtronicDeviceStatusChange(RileyLinkServiceState rileyLinkServiceState, RileyLinkError rileyLinkError) {
        this.rileyLinkServiceState = rileyLinkServiceState;
        this.rileyLinkError = rileyLinkError;
    }


    public EventMedtronicDeviceStatusChange(PumpDeviceState pumpDeviceState) {
        this.pumpDeviceState = pumpDeviceState;
    }


    public EventMedtronicDeviceStatusChange(PumpDeviceState pumpDeviceState, String errorDescription) {
        this.pumpDeviceState = pumpDeviceState;
        this.errorDescription = errorDescription;
    }


    @Override
    public String toString() {
        return "EventMedtronicDeviceStatusChange [" + "rileyLinkServiceState=" + rileyLinkServiceState
            + ", rileyLinkError=" + rileyLinkError + ", pumpDeviceState=" + pumpDeviceState + ']';
    }
}
