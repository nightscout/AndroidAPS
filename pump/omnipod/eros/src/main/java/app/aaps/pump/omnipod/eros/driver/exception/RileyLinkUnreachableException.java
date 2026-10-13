package app.aaps.pump.omnipod.eros.driver.exception;

// Indicates that we didn't get any response from the RL
public class RileyLinkUnreachableException extends OmnipodException {
    public RileyLinkUnreachableException() {
        super("Timeout in communication between phone and RileyLink", false);
    }
}
