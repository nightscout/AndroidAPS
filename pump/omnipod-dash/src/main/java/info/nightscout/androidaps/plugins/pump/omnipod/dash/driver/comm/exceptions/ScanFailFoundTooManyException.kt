package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.scan.BleDiscoveredDevice
import java.util.*

class ScanFailFoundTooManyException(devices: List<BleDiscoveredDevice>) : ScanException("Found more than one Pod") {

    private val devices: List<BleDiscoveredDevice> = ArrayList(devices)
    val discoveredDevices: List<BleDiscoveredDevice>
        get() = Collections.unmodifiableList(devices)
}
