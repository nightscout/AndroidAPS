package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.exception;

public class MessageDecodingException extends OmnipodException {
    public MessageDecodingException(String message) {
        super(message, false);
    }

    public MessageDecodingException(String message, Throwable cause) {
        super(message, cause, false);
    }
}
