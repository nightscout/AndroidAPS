package info.nightscout.androidaps.plugins.pump.omnipod.driver.exception;

public class CommandInitializationException extends OmnipodException {
    public CommandInitializationException(String message) {
        super(message, true);
    }

    public CommandInitializationException(String message, Throwable cause) {
        super(message, cause, true);
    }
}
