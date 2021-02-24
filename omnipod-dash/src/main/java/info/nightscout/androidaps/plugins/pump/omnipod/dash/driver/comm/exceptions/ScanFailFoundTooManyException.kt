package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.scan.BleDiscoveredDevice
import java.util.*

class ScanFailFoundTooManyException(devices: List<BleDiscoveredDevice>?) : ScanFailException() {

    private val devices: List<BleDiscoveredDevice>
    val discoveredDevices: List<BleDiscoveredDevice>
        get() = Collections.unmodifiableList(devices)

    init {
        this.devices = ArrayList(devices)
    }
}