package info.nightscout.androidaps.plugins.pump.omnipod.exception;

import java.util.Locale;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.podinfo.PodInfoFaultEvent;

public class PodFaultException extends OmnipodException {
    private final PodInfoFaultEvent faultEvent;

    public PodFaultException(PodInfoFaultEvent faultEvent) {
        super(describePodFault(faultEvent));
        this.faultEvent = faultEvent;
    }

    public PodFaultException(PodInfoFaultEvent faultEvent, Throwable cause) {
        super(describePodFault(faultEvent), cause);
        this.faultEvent = faultEvent;
    }

    public static String describePodFault(PodInfoFaultEvent faultEvent) {
        return String.format(Locale.getDefault(), "Pod fault (%d): %s", faultEvent.getFaultEventCode().getValue(),
                faultEvent.getFaultEventCode().toString());
    }

    public PodInfoFaultEvent getFaultEvent() {
        return faultEvent;
    }

    @Override
    public void printStackTrace() {
        System.out.println(faultEvent.toString());
        super.printStackTrace();
    }
}
