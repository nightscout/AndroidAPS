package info.nightscout.androidaps.plugins.pump.omnipod.comm.exception;

import java.util.Locale;

import info.nightscout.androidaps.plugins.pump.omnipod.exception.OmnipodException;

public class CrcMismatchException extends OmnipodException {
    private final int expected;
    private final int actual;

    public CrcMismatchException(int expected, int actual) {
        super(String.format(Locale.getDefault(), "CRC mismatch: expected %d, got %d", expected, actual), false);
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
