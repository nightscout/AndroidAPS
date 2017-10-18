package de.jotomo.ruffyscripter.commands;

import de.jotomo.ruffy.spi.CommandResult;

public class CommandException extends RuntimeException {
    public boolean success = false;
    public boolean enacted = false;
    public Exception exception = null;
    public String message = null;

    public CommandException() {
    }

    public CommandException success(boolean success) {
        this.success = success;
        return this;
    }

    public CommandException enacted(boolean enacted) {
        this.enacted = enacted;
        return this;
    }

    public CommandException exception(Exception exception) {
        this.exception = exception;
        return this;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public CommandException message(String message) {
        this.message = message;
        return this;
    }

    public CommandResult toCommandResult() {
        return new CommandResult().success(success).enacted(enacted).exception(exception).message(message);
    }

    @Override
    public String toString() {
        return "CommandException{" +
                "success=" + success +
                ", enacted=" + enacted +
                ", exception=" + exception +
                ", message='" + message + '\'' +
                '}';
    }
}
