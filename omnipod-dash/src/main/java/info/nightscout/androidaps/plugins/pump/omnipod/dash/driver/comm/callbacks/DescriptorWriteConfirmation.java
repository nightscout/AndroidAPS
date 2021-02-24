package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.callbacks;

public class DescriptorWriteConfirmation {
    public int status;
    public String uuid;

    public DescriptorWriteConfirmation(int status, String uuid) {
        this.status = status;
        this.uuid = uuid;
    }
}
