package app.aaps.pump.omnipod.eros.driver.exception;

public class CommandFailedAfterChangingDeliveryStatusException extends OmnipodException {
    public CommandFailedAfterChangingDeliveryStatusException(String message, Throwable cause) {
        super(message, cause, false);
    }
}
