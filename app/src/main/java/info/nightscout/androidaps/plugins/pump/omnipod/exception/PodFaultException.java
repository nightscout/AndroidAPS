package info.nightscout.androidaps.plugins.pump.omnipod.exception;

import java.util.Locale;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.podinfo.PodInfoFaultEvent;

public class PodFaultException extends OmnipodException {
    private final PodInfoFaultEvent faultEvent;

    public PodFaultException(PodInfoFaultEvent faultEvent) {
        super(String.format(Locale.getDefault(), "Pod fault (%d): %s", faultEvent.getFaultEventCode().getValue(),
                faultEvent.getFaultEventCode().toString()), true);
        this.faultEvent = faultEvent;
    }

    public PodInfoFaultEvent getFaultEvent() {
        return faultEvent;
    }
}
