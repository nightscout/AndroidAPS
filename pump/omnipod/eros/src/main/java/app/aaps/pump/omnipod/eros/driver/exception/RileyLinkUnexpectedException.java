package app.aaps.pump.omnipod.eros.driver.exception;

public class RileyLinkUnexpectedException extends OmnipodException {
    public RileyLinkUnexpectedException(Throwable cause) {
        super("Unexpected Exception during RileyLink communication", cause, false);
    }
}
