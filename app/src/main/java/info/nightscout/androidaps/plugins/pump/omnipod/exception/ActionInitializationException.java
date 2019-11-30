package info.nightscout.androidaps.plugins.pump.omnipod.exception;

public class ActionInitializationException extends OmnipodException {
    public ActionInitializationException(String message) {
        super(message);
    }

    @Override
    public boolean isCertainFailure() {
        return true;
    }
}
