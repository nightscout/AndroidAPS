package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs;

public class RileyLinkPumpInfo {
    private final String connectedDeviceModel;
    private final String pumpDescription;
    private final String connectedDeviceSerialNumber;
    private final String pumpFrequency;

    public RileyLinkPumpInfo(String pumpDescription, String pumpFrequency, String connectedDeviceModel, String connectedDeviceSerialNumber) {
        this.pumpDescription = pumpDescription;
        this.pumpFrequency = pumpFrequency;
        this.connectedDeviceModel = connectedDeviceModel;
        this.connectedDeviceSerialNumber = connectedDeviceSerialNumber;
    }

    public String getConnectedDeviceModel() {
        return connectedDeviceModel;
    }

    public String getPumpDescription() {
        return pumpDescription;
    }

    public String getConnectedDeviceSerialNumber() {
        return connectedDeviceSerialNumber;
    }

    public String getPumpFrequency() {
        return pumpFrequency;
    }
}
