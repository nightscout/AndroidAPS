package info.nightscout.androidaps.plugins.pump.omnipod.comm.exception;

import java.util.Locale;

import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodProgressStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.exception.OmnipodException;

public class IllegalPodProgressException extends OmnipodException {
    private final PodProgressStatus expected;
    private final PodProgressStatus actual;

    public IllegalPodProgressException(PodProgressStatus expected, PodProgressStatus actual) {
        super(String.format(Locale.getDefault(), "Illegal setup state: %s, expected: %s", actual, expected), true);
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
