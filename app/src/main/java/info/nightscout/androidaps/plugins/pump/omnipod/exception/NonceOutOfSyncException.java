package info.nightscout.androidaps.plugins.pump.omnipod.exception;

public class NonceOutOfSyncException extends OmnipodException {
    public NonceOutOfSyncException() {
        super("Nonce out of sync", true);
    }
}
