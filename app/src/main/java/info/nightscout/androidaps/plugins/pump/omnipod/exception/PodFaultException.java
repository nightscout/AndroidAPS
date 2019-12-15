package info.nightscout.androidaps.plugins.pump.omnipod.exception;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.podinfo.PodInfoFaultEvent;

public class PodFaultException extends OmnipodException {
    private final PodInfoFaultEvent faultEvent;

    public PodFaultException(PodInfoFaultEvent faultEvent) {
        super(faultEvent.getFaultEventType().toString(), true);
        this.faultEvent = faultEvent;
    }

    public PodInfoFaultEvent getFaultEvent() {
        return faultEvent;
    }
}
