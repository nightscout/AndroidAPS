package info.nightscout.androidaps.plugins.pump.omnipod.exception;

public class OmnipodException extends RuntimeException {
    public OmnipodException(String message) {
        super(message);
    }

    public OmnipodException(String message, Throwable cause) {
        super(message, cause);
    }
}
