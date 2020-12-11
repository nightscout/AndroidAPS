package info.nightscout.androidaps.plugins.pump.omnipod.driver.exception;

import java.util.Locale;

import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.ActivationProgress;

public class IllegalActivationProgressException extends OmnipodException {
    private final ActivationProgress expected;
    private final ActivationProgress actual;

    public IllegalActivationProgressException(ActivationProgress expected, ActivationProgress actual) {
        super(String.format(Locale.getDefault(), "Illegal activation progress: %s, expected: %s", actual, expected), true);
        this.expected = expected;
        this.actual = actual;
    }

    public ActivationProgress getExpected() {
        return expected;
    }

    public ActivationProgress getActual() {
        return actual;
    }
}
