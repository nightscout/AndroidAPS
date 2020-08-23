package info.nightscout.androidaps.plugins.pump.omnipod.driver.exception;

public class NonceResyncException extends OmnipodException {
    public NonceResyncException() {
        super("Nonce resync failed", true);
    }
}
