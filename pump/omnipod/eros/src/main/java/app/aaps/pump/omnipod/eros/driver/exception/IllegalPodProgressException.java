package app.aaps.pump.omnipod.eros.driver.exception;

import java.util.Locale;

import app.aaps.pump.omnipod.eros.driver.definition.PodProgressStatus;

public class IllegalPodProgressException extends OmnipodException {
    private final PodProgressStatus expected;
    private final PodProgressStatus actual;

    public IllegalPodProgressException(PodProgressStatus expected, PodProgressStatus actual) {
        super(String.format(Locale.getDefault(), "Illegal Pod progress: %s, expected: %s", actual, expected), true);
        this.expected = expected;
        this.actual = actual;
    }

    public PodProgressStatus getExpected() {
        return expected;
    }

    public PodProgressStatus getActual() {
        return actual;
    }
}
