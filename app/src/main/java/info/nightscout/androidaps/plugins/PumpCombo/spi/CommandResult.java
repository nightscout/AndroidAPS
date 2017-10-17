package info.nightscout.androidaps.plugins.PumpCombo.spi;

import java.util.Date;

public class CommandResult {
    public String request;
    public boolean success;
    public boolean enacted;
    public long completionTime;
    public Exception exception;
    public String message;
    public PumpState state;
    public PumpHistory history;
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

    @Override
    public String toString() {
        return "CommandResult{" +
                "request=" + request +
                ", success=" + success +
                ", enacted=" + enacted +
                ", completionTime=" + completionTime + "(" + new Date(completionTime) + ")" +
                "' duration=" + duration +
                ", exception=" + exception +
                ", message='" + message + '\'' +
                ", state=" + state +
                '}';
    }
}
