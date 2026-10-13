package app.aaps.pump.omnipod.common.bledriver.comm.interfaces.session

/**
 * Factory to create a BLE connection from a pod address.
 * Implemented by Bluetooth library-specific adapters.
 */
interface BleConnectionFactory {

    fun createConnection(podAddress: String): BleConnection
}
