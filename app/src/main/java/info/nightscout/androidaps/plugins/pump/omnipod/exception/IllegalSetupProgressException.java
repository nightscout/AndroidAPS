package info.nightscout.androidaps.plugins.pump.omnipod.exception;

import java.util.Locale;

import info.nightscout.androidaps.plugins.pump.omnipod.defs.SetupProgress;

public class IllegalSetupProgressException extends OmnipodException {
    private final SetupProgress expected;
    private final SetupProgress actual;

    public IllegalSetupProgressException(SetupProgress expected, SetupProgress actual) {
        super(String.format(Locale.getDefault(), "Illegal setup progress: %s, expected: %s", actual, expected), true);
        this.expected = expected;
        this.actual = actual;
    }

    public SetupProgress getExpected() {
        return expected;
    }

    public SetupProgress getActual() {
        return actual;
    }

}
