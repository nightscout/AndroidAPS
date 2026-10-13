package app.aaps.pump.omnipod.eros.driver.exception;

public class NonceResyncException extends OmnipodException {
    public NonceResyncException() {
        super("Nonce resync failed", true);
    }
}
