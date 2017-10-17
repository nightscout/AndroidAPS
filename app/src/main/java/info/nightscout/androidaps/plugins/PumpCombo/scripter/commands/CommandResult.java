package info.nightscout.androidaps.plugins.PumpCombo.scripter.commands;

import java.util.Date;

import info.nightscout.androidaps.plugins.PumpCombo.scripter.History;
import info.nightscout.androidaps.plugins.PumpCombo.scripter.PumpState;

public class CommandResult {
    public boolean success;
    public boolean enacted;
    public long completionTime;
    public Exception exception;
    public String message;
    public PumpState state;
    public History history;
    public String duration;

    public CommandResult() {
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

    public CommandResult history(History history) {
        this.history = history;
        return this;
    }

    @Override
    public String toString() {
        return "CommandResult{" +
                "success=" + success +
                ", enacted=" + enacted +
                ", completionTime=" + completionTime + "(" + new Date(completionTime) + ")" +
                "' duration=" + duration +
                ", exception=" + exception +
                ", message='" + message + '\'' +
                ", state=" + state +
                '}';
    }
}
