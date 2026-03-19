package app.aaps.pump.danars.services

/**
 * Abstraction layer replacing direct Android BluetoothAdapter/BluetoothGatt/BluetoothLeScanner access.
 *
 * Organized into sub-interfaces mirroring the Android Bluetooth API structure:
 * - [BleAdapter] — adapter-level operations (device lookup, bonding, enable)
 * - [BleScanner] — BLE scanning for device discovery
 * - [BleGatt] — GATT connection, services, characteristics, data transfer
 *
 * Two implementations:
 * - [BleTransportImpl] wraps real Android Bluetooth stack (production)
 * - EmulatorBleTransport in :pump:danars-emulator (testing)
 */
interface BleTransport {

    val adapter: BleAdapter
    val scanner: BleScanner
    val gatt: BleGatt

    fun setListener(listener: BleTransportListener?)
}

interface BleAdapter {

    fun enable()
    fun getDeviceName(address: String): String?
    fun isDeviceBonded(address: String): Boolean
    fun createBond(address: String): Boolean
    fun removeBond(address: String)
}

data class ScannedDevice(val name: String, val address: String)

interface BleScanner {

    fun startScan(onDeviceFound: (ScannedDevice) -> Unit)
    fun stopScan()
}

interface BleGatt {

    fun connect(address: String): Boolean
    fun disconnect()
    fun close()
    fun discoverServices()
    fun findCharacteristics(): Boolean
    fun enableNotifications()
    fun writeCharacteristic(data: ByteArray)
}

interface BleTransportListener {

    fun onConnectionStateChanged(connected: Boolean)
    fun onServicesDiscovered(success: Boolean)
    fun onDescriptorWritten()
    fun onCharacteristicChanged(data: ByteArray)
    fun onCharacteristicWritten()
}
