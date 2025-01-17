package app.aaps.pump.omnipod.dash.driver.comm.scan

import android.os.ParcelUuid

class DiscoveredInvalidPodException(message: String, serviceUUIds: List<ParcelUuid?>) :
    Exception("$message service UUIDs: $serviceUUIds")
