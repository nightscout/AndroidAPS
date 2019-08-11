package info.nightscout.androidaps.plugins.pump.omnipod.exception;

public class NotEnoughDataException extends OmnipodException {
    public NotEnoughDataException(String message) {
        super(message);
    }

    public NotEnoughDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
