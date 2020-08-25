package info.nightscout.androidaps.plugins.pump.omnipod.comm.exception;

import info.nightscout.androidaps.plugins.pump.omnipod.exception.OmnipodException;

public class ActionInitializationException extends OmnipodException {
    public ActionInitializationException(String message) {
        super(message, true);
    }
}
