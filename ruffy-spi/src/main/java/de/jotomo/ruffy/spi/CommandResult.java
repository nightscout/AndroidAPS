package de.jotomo.ruffy.spi;

import java.util.List;

import de.jotomo.ruffy.spi.history.PumpHistory;

public class CommandResult {
    /** The request made made to the pump, like setting a TBR. */
    public String request;
    /** Whether the command was executed successfully. */
    public boolean success;
    /** Whether any changes were made, e.g. if a the request was to cancel a running TBR,
     * but not TBR was active, this will be false. */
    public boolean enacted;
    /** Time the command completed. */
    public long completionTime;
    /** Null unless an unhandled exception was raised. */
    public Exception exception;
    /** (Error)message describing the result of the command. */
    public String message;
    /** State of the pump *after* command execution. */
    public PumpState state;
    /** History if requested by the command. */
    public PumpHistory history;
    /** Basal rate profile if requested. */
    public List<BasalProfile> basalProfiles;
    /** Total duration the command took. */
    public String duration;

    public CommandResult() {
    }

    public CommandResult request(String request) {
        this.request = request;
        return this;
    }

    public CommandResult success(boolean success) {
        this.success = success;
        return this;
    }

    public CommandResult enacted(boolean enacted) {
        this.enacted = enacted;
        return this;
    }

    public CommandResult completionTime(long completionTime) {
        this.completionTime = completionTime;
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

    public CommandResult message(String message) {
        this.message = message;
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

    public CommandResult basalProfile(List<BasalProfile> basalProfiles) {
        this.basalProfiles = basalProfiles;
        return this;
    }

    @Override
    public String toString() {
        return "CommandResult{" +
                "request='" + request + '\'' +
                ", success=" + success +
                ", enacted=" + enacted +
                ", completionTime=" + completionTime +
                ", exception=" + exception +
                ", message='" + message + '\'' +
                ", state=" + state +
                ", history=" + history +
                ", basalProfiles=" + basalProfiles +
                ", duration='" + duration + '\'' +
                '}';
    }
}
