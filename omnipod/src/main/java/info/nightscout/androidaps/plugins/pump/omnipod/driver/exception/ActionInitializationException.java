package info.nightscout.androidaps.plugins.pump.omnipod.driver.exception;

public class ActionInitializationException extends OmnipodException {
    public ActionInitializationException(String message) {
        super(message, true);
    }
}
