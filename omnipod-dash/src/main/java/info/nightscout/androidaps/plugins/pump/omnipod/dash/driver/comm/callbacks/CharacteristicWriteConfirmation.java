package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.callbacks;

public class CharacteristicWriteConfirmation {
    public byte[] payload;
    public int status;

    public CharacteristicWriteConfirmation(byte[] payload, int status) {
        this.payload = payload;
        this.status = status;
    }
}
