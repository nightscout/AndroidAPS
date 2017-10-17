package info.nightscout.androidaps.plugins.PumpCombo.spi;

public interface BolusProgressReporter {
    enum State {
        PROGRAMMING,
        DELIVERING,
        DELIVERED,
        STOPPING,
        STOPPED
    }

    void report(State state, int percent, double delivered);
}
