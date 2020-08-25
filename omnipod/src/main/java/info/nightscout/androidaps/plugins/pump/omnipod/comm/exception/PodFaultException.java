package info.nightscout.androidaps.plugins.pump.omnipod.comm.exception;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.podinfo.PodInfoFaultEvent;
import info.nightscout.androidaps.plugins.pump.omnipod.exception.OmnipodException;

public class PodFaultException extends OmnipodException {
    private final PodInfoFaultEvent faultEvent;

    public PodFaultException(PodInfoFaultEvent faultEvent) {
        super(faultEvent.getFaultEventCode().toString(), true);
        this.faultEvent = faultEvent;
    }

    public PodInfoFaultEvent getFaultEvent() {
        return faultEvent;
    }
}
