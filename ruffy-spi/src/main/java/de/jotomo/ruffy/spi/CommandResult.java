package de.jotomo.ruffy.spi;

import android.support.annotation.Nullable;

import de.jotomo.ruffy.spi.history.Bolus;
import de.jotomo.ruffy.spi.history.PumpHistory;

public class CommandResult {
    /** Whether the command was executed successfully. */
    public boolean success;
    /** Null unless an unhandled exception was raised. */
    public Exception exception;
    /** State of the pump *after* command execution. */
    public PumpState state;
    /** History if requested by the command. */
    @Nullable
    public PumpHistory history;
    /** Basal rate profile if requested. */
    public BasalProfile basalProfile;
    /** Total duration the command took. */
    public String duration;

    /** Whether an alert (warning only) was confirmed. This can happen during boluses.
     * Request error history to see which errors occurred. */
    // TODO check usage
    // TODO return alerts to display? 'forwardedAlert'?
    public boolean alertConfirmed;
    /** BolusCommand: if a cancel request was successful */
    public boolean wasSuccessfullyCancelled;

    public int reservoirLevel = -1;

    @Nullable
    public Bolus lastBolus;

    public CommandResult success(boolean success) {
        this.success = success;
        return this;
    }

    public CommandResult duration(String duration) {
        this.duration = duration;
        return this;
    }

    public CommandResult exception(Exception exception) {
        this.exception = exception;
        return this;
    }

    public CommandResult state(PumpState state) {
        this.state = state;
        return this;
    }

    public CommandResult history(PumpHistory history) {
        this.history = history;
        return this;
    }

    public CommandResult basalProfile(BasalProfile basalProfile) {
        this.basalProfile = basalProfile;
        return this;
    }

    @Override
    public String toString() {
        return "CommandResult{" +
                "success=" + success +
                ", exception=" + exception +
                ", state=" + state +
                ", history=" + history +
                ", basalProfile=" + basalProfile +
                ", duration='" + duration + '\'' +
                ", alertConfirmed='" + alertConfirmed + '\'' +
                ", wasSuccessfullyCancelled='" + wasSuccessfullyCancelled + '\'' +
                '}';
    }
}
