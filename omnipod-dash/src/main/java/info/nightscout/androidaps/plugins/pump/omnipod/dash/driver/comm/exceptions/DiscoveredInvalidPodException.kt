package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions

import android.os.ParcelUuid

class DiscoveredInvalidPodException : Exception {
    constructor(message: String) : super(message)
    constructor(message: String, serviceUUIds: List<ParcelUuid?>) : super("$message service UUIDs: $serviceUUIds")
}