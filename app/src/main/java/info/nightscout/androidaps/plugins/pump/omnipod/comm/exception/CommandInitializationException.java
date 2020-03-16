package info.nightscout.androidaps.plugins.pump.omnipod.comm.exception;

import info.nightscout.androidaps.plugins.pump.omnipod.exception.OmnipodException;

public class CommandInitializationException extends OmnipodException {
    public CommandInitializationException(String message) {
        super(message, true);
    }

    public CommandInitializationException(String message, Throwable cause) {
        super(message, cause, true);
    }
}
