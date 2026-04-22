package app.aaps.pump.omnipod.common.bledriver.comm.interfaces.scan

/**
 * BLE scan for pod discovery.
 */
interface PodScanner {

    /**
     * Scan for a pod advertising the given service UUID, validating pod ID from scan record.
     * @throws ScanException if no pod found or scan failed
     * @throws ScanFailFoundTooManyException if multiple matching pods found
     */
    fun scanForPod(serviceUUID: String?, podID: Long): BleDiscoveredDevice

    companion object {
        const val SCAN_FOR_SERVICE_UUID = "00004024-0000-1000-8000-00805F9B34FB"
        const val POD_ID_NOT_ACTIVATED = 0xFFFFFFFEL
    }
}
