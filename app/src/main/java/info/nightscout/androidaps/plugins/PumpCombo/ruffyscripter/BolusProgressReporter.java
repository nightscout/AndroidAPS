package info.nightscout.androidaps.plugins.PumpCombo.ruffyscripter;

public interface BolusProgressReporter {
    enum State {
        PROGRAMMING,
        DELIVERING,
        DELIVERED,
        STOPPING,
        STOPPED,
        RECOVERING
    }

    void report(State state, int percent, double delivered);
}
