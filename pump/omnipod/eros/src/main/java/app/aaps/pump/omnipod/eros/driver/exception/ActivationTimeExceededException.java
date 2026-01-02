package app.aaps.pump.omnipod.eros.driver.exception;

public class ActivationTimeExceededException extends OmnipodException {
    public ActivationTimeExceededException() {
        super("The Pod's activation time has been exceeded", true);
    }
}
