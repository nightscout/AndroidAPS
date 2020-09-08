package info.nightscout.androidaps.plugins.pump.omnipod.driver.exception;

import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.DeliveryStatus;

public class DeliveryStatusVerificationFailedException extends OmnipodException {
    private final DeliveryStatus expectedStatus;

    public DeliveryStatusVerificationFailedException(DeliveryStatus expectedStatus, Throwable cause) {
        super("Failed to verify delivery status (expected=" + expectedStatus + ")", cause, false);
        this.expectedStatus = expectedStatus;
    }

    public DeliveryStatus getExpectedStatus() {
        return expectedStatus;
    }
}
