package app.aaps.pump.omnipod.eros.driver.exception;

import app.aaps.pump.omnipod.eros.driver.definition.PodProgressStatus;

public class PodProgressStatusVerificationFailedException extends OmnipodException {
    private final PodProgressStatus expectedStatus;

    public PodProgressStatusVerificationFailedException(PodProgressStatus expectedStatus, Throwable cause) {
        super("Failed to verify Pod progress status (expected=" + expectedStatus + ")", cause, false);
        this.expectedStatus = expectedStatus;
    }

    public PodProgressStatus getExpectedStatus() {
        return expectedStatus;
    }
}
