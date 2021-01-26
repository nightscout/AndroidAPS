package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs;

public class RileyLinkPumpInfo {
    private final String pumpFrequency;
    private final String connectedDeviceModel;
    private final String connectedDeviceSerialNumber;

    public RileyLinkPumpInfo(String pumpFrequency, String connectedDeviceModel, String connectedDeviceSerialNumber) {
        this.pumpFrequency = pumpFrequency;
        this.connectedDeviceModel = connectedDeviceModel;
        this.connectedDeviceSerialNumber = connectedDeviceSerialNumber;
    }

    public String getConnectedDeviceModel() {
        return connectedDeviceModel;
    }

    public String getConnectedDeviceSerialNumber() {
        return connectedDeviceSerialNumber;
    }

    public String getPumpFrequency() {
        return pumpFrequency;
    }
}
