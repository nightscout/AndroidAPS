package info.nightscout.androidaps.plugins.pump.omnipod.comm.exception;

import info.nightscout.androidaps.plugins.pump.omnipod.exception.OmnipodException;

public class IllegalVersionResponseTypeException extends OmnipodException {
    public IllegalVersionResponseTypeException(String expected, String actual) {
        super("Invalid Version Response type. Expected=" + expected + ", actual=" + actual, false);
    }
}
