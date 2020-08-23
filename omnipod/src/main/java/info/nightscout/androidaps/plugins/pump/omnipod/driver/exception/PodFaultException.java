package info.nightscout.androidaps.plugins.pump.omnipod.driver.exception;

import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.response.podinfo.PodInfoFaultEvent;

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
