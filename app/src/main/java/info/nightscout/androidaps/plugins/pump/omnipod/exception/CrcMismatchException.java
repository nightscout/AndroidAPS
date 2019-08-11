package info.nightscout.androidaps.plugins.pump.omnipod.exception;

public class CrcMismatchException extends OmnipodException {
    public CrcMismatchException(String message) {
        super(message);
    }

    public CrcMismatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
