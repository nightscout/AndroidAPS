package info.nightscout.androidaps.plugins.pump.omnipod.exception;

public class NonceResyncException extends OmnipodException {
    public NonceResyncException() {
        super("Nonce resync failed");
    }

    @Override
    public boolean isCertainFailure() {
        return true;
    }
}
