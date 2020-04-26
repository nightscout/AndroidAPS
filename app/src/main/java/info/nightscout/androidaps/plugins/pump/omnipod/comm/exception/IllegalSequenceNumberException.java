package info.nightscout.androidaps.plugins.pump.omnipod.comm.exception;

import info.nightscout.androidaps.plugins.pump.omnipod.exception.OmnipodException;

public class IllegalSequenceNumberException extends OmnipodException {
    private final int expected;
    private final int actual;

    public IllegalSequenceNumberException(int expected, int actual) {
        super("Invalid sequence number. Expected="+ expected +", actual="+ actual, false);
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
