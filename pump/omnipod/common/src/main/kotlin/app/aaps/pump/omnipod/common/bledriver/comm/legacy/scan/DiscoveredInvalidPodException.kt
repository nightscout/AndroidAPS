package app.aaps.pump.omnipod.common.bledriver.comm.legacy.scan

import android.os.ParcelUuid

class DiscoveredInvalidPodException(message: String, serviceUUIds: List<ParcelUuid?>) :
    Exception("$message service UUIDs: $serviceUUIds")
