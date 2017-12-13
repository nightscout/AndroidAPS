package de.jotomo.ruffy.spi;

public interface BolusProgressReporter {
    enum State {
        PROGRAMMING,
        DELIVERING,
        DELIVERED,
        STOPPING,
        STOPPED,
        RECOVERING,
        FINISHED
    }

    void report(State state, int percent, double delivered);
}
