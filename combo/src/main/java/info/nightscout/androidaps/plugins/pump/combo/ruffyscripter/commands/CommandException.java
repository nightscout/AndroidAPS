package info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.commands;

public class CommandException extends RuntimeException {
    public CommandException(String message) {
        super(message);
    }

    public CommandException(String message, Exception exception) {
        super(message, exception);
    }
}
