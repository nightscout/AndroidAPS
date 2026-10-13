package app.aaps.pump.omnipod.eros.driver.exception;

import java.util.Locale;

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
