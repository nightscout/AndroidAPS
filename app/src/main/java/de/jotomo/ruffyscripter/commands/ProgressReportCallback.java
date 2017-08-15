package de.jotomo.ruffyscripter.commands;

public interface ProgressReportCallback {
    enum State {
        PREPARING,
        BOLUSING,
        CANCELLING,
        FINISHED,
        CANCELLED
    }

    void progress(State state, int percent, double delivered);
}
