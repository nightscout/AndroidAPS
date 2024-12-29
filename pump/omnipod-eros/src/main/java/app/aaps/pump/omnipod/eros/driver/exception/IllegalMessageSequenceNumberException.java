package app.aaps.pump.omnipod.eros.driver.exception;

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
