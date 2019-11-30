package info.nightscout.androidaps.plugins.pump.omnipod.exception;

public class CommandInitializationException extends OmnipodException {
    public CommandInitializationException(String message) {
        super(message);
    }

    @Override
    public boolean isCertainFailure() {
        return true;
    }
}
