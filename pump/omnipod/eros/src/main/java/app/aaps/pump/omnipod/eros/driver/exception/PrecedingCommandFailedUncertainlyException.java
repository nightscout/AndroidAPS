package app.aaps.pump.omnipod.eros.driver.exception;

public class PrecedingCommandFailedUncertainlyException extends OmnipodException {
    public PrecedingCommandFailedUncertainlyException(Throwable cause) {
        super("Preceding command failed", cause, false);
    }
}
