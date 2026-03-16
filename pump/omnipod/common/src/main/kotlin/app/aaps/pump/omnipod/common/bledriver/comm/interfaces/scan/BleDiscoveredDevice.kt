package app.aaps.pump.omnipod.common.bledriver.comm.interfaces.scan

/**
 * Result of a successful pod scan.
 * Implementations hold Bluetooth library-specific scan data (e.g. Android ScanResult).
 */
interface BleDiscoveredDevice {

    val address: String
}
