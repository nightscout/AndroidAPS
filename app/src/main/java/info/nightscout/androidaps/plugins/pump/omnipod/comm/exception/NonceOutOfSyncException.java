package info.nightscout.androidaps.plugins.pump.omnipod.comm.exception;

import info.nightscout.androidaps.plugins.pump.omnipod.exception.OmnipodException;

public class NonceOutOfSyncException extends OmnipodException {
    public NonceOutOfSyncException() {
        super("Nonce out of sync", true);
    }
}
