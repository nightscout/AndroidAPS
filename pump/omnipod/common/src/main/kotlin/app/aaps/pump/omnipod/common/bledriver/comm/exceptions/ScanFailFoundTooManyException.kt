package app.aaps.pump.omnipod.common.bledriver.comm.exceptions

import app.aaps.pump.omnipod.common.bledriver.comm.scan.BleDiscoveredDevice
import java.util.*

class ScanFailFoundTooManyException(devices: List<BleDiscoveredDevice>) : ScanException("Found more than one Pod") {

    private val devices: List<BleDiscoveredDevice> = ArrayList(devices)
    val discoveredDevices: List<BleDiscoveredDevice>
        get() = Collections.unmodifiableList(devices)
}
