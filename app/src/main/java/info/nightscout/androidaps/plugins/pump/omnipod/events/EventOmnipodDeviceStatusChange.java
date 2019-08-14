package info.nightscout.androidaps.plugins.pump.omnipod.events;

import info.nightscout.androidaps.events.Event;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkError;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodDeviceState;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodSessionState;

/**
 * Created by andy on 4.8.2019
 */
public class EventOmnipodDeviceStatusChange extends Event {

    public RileyLinkServiceState rileyLinkServiceState;
    public RileyLinkError rileyLinkError;

    public PodSessionState podSessionState;
    public String errorDescription;
    public PodDeviceState podDeviceState;


    public EventOmnipodDeviceStatusChange(RileyLinkServiceState rileyLinkServiceState) {
        this(rileyLinkServiceState, null);
    }


    public EventOmnipodDeviceStatusChange(RileyLinkServiceState rileyLinkServiceState, RileyLinkError rileyLinkError) {
        this.rileyLinkServiceState = rileyLinkServiceState;
        this.rileyLinkError = rileyLinkError;
    }


    public EventOmnipodDeviceStatusChange(PodSessionState podSessionState) {
        this.podSessionState = podSessionState;
    }


    public EventOmnipodDeviceStatusChange(String errorDescription) {
        this.errorDescription = errorDescription;
    }

    public EventOmnipodDeviceStatusChange(PodDeviceState podDeviceState, String errorDescription) {
        this.podDeviceState = podDeviceState;
        this.errorDescription = errorDescription;
    }


    @Override
    public String toString() {
        return "EventOmnipodDeviceStatusChange [" //
                + "rileyLinkServiceState=" + rileyLinkServiceState
                + ", rileyLinkError=" + rileyLinkError //
                + ", podSessionState=" + podSessionState //
                + ", podDeviceState=" + podDeviceState + "]";
    }
}
