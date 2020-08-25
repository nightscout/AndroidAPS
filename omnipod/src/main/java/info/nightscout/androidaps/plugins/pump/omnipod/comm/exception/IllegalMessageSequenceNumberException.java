package info.nightscout.androidaps.plugins.pump.omnipod.comm.exception;

import info.nightscout.androidaps.plugins.pump.omnipod.exception.OmnipodException;

public class IllegalMessageSequenceNumberException extends OmnipodException {
    private final int expected;
    private final int actual;

    public IllegalMessageSequenceNumberException(int expected, int actual) {
        super("Invalid message sequence number. Expected=" + expected + ", actual=" + actual, false);
        this.expected = expected;
        this.actual = actual;
    }

    public int getExpected() {
        return expected;
    }

    public int getActual() {
        return actual;
    }
}
