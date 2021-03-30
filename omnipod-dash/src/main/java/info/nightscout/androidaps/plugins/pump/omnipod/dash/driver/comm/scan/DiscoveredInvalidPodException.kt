package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.scan

import android.os.ParcelUuid

class DiscoveredInvalidPodException : Exception {
    constructor(message: String, serviceUUIds: List<ParcelUuid?>) : super("$message service UUIDs: $serviceUUIds")
}
