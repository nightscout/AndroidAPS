package info.nightscout.androidaps.plugins.pump.omnipod.driver.exception;

import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.PodProgressStatus;

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
