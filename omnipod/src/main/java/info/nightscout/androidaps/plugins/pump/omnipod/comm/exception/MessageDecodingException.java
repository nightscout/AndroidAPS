package info.nightscout.androidaps.plugins.pump.omnipod.comm.exception;

import info.nightscout.androidaps.plugins.pump.omnipod.exception.OmnipodException;

public class MessageDecodingException extends OmnipodException {
    public MessageDecodingException(String message) {
        super(message, false);
    }

    public MessageDecodingException(String message, Throwable cause) {
        super(message, cause, false);
    }
}
