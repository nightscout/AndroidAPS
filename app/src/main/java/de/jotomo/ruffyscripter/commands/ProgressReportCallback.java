package de.jotomo.ruffyscripter.commands;

public interface ProgressReportCallback {
    enum State {
        DELIVERING,
        DELIVERED,
        STOPPING,
        STOPPED
    }

    void report(State state, int percent, double delivered);
}
