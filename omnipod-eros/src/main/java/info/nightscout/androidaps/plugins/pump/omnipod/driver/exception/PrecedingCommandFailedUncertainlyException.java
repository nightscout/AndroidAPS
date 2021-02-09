package info.nightscout.androidaps.plugins.pump.omnipod.driver.exception;

public class PrecedingCommandFailedUncertainlyException extends OmnipodException {
    public PrecedingCommandFailedUncertainlyException(Throwable cause) {
        super("Preceding command failed", cause, false);
    }
}
