package info.nightscout.androidaps.plugins.pump.omnipod.comm.exception;

import info.nightscout.androidaps.plugins.pump.omnipod.exception.OmnipodException;

public class NonceResyncException extends OmnipodException {
    public NonceResyncException() {
        super("Nonce resync failed", true);
    }
}
