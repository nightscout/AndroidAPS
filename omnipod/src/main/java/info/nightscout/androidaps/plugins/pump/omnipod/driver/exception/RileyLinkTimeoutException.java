package info.nightscout.androidaps.plugins.pump.omnipod.driver.exception;

// Response indicating that there was a timeout in communication between the RileyLink and the Pod
public class RileyLinkTimeoutException extends OmnipodException {
    public RileyLinkTimeoutException() {
        super("Timeout in communication between RileyLink and Pod", false);
    }
}
