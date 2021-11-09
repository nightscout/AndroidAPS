package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.exception;

public class RileyLinkInterruptedException extends OmnipodException {
    public RileyLinkInterruptedException() {
        super("RileyLink interrupted", false);
    }
}
