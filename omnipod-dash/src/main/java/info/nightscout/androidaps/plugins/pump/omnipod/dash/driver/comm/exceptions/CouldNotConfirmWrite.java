package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions;

public class CouldNotConfirmWrite extends Exception {

    private final byte[] sent;
    private final Object confirmed;

    public CouldNotConfirmWrite(byte[] sent, byte[] confirmed) {
        super();
        this.sent = sent;
        this.confirmed = confirmed;
    }
}
