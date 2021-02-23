package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.scan.BleDiscoveredDevice;

public class ScanFailFoundTooManyException extends ScanFailException {
    private final List<BleDiscoveredDevice> devices;

    public ScanFailFoundTooManyException(List<BleDiscoveredDevice> devices) {
        super();
        this.devices = new ArrayList<>(devices);
    }

    public List<BleDiscoveredDevice> getDiscoveredDevices() {
        return Collections.unmodifiableList(this.devices);
    }
}
