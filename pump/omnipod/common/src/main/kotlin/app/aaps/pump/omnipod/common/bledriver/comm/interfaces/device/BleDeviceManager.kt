package app.aaps.pump.omnipod.common.bledriver.comm.interfaces.device

import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.scan.PodScanner

/**
 * Abstraction for BLE device access and bonding.
 * Implemented by Bluetooth library-specific adapters.
 */
interface BleDeviceManager {

    /**
     * Ensure device is bonded if bonding is enabled. Call before connect.
     * @return true if ready to connect (bonded or bonding not required)
     */
    fun ensureBondedIfRequired(podAddress: String): Boolean

    /**
     * Remove bond for the given address. Used for unpair.
     */
    fun removeBond(podAddress: String)

    /**
     * Check if Bluetooth adapter is available.
     */
    fun isBluetoothAvailable(): Boolean

    /**
     * Create a PodScanner for discovering pods.
     */
    fun createPodScanner(): PodScanner
}
