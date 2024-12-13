package app.aaps.pump.omnipod.eros.driver.exception;

import app.aaps.pump.omnipod.eros.driver.communication.message.response.podinfo.PodInfoDetailedStatus;

public class PodFaultException extends OmnipodException {
    private final PodInfoDetailedStatus detailedStatus;

    public PodFaultException(PodInfoDetailedStatus detailedStatus) {
        super(detailedStatus.getFaultEventCode().toString(), true);
        this.detailedStatus = detailedStatus;
    }

    public PodInfoDetailedStatus getDetailedStatus() {
        return detailedStatus;
    }
}
