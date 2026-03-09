package app.aaps.pump.danars.services

/**
 * Abstraction layer replacing direct Android BluetoothAdapter/BluetoothGatt access.
 *
 * Two implementations:
 * - [BleTransportImpl] wraps real Android Bluetooth stack (production)
 * - EmulatorBleTransport in :pump:danars-emulator (testing)
 *
 * This allows BLEComm to be tested end-to-end with a pump emulator.
 */
interface BleTransport {

    // Device info
    fun getDeviceName(address: String): String?
    fun isDeviceBonded(address: String): Boolean
    fun createBond(address: String): Boolean
    fun removeBond(address: String)

    // Connection lifecycle
    fun connectGatt(address: String): Boolean
    fun disconnectGatt()
    fun closeGatt()

    // Service discovery & characteristics
    fun discoverServices()
    fun findCharacteristics(): Boolean
    fun enableNotifications()

    // Data transfer
    fun writeCharacteristic(data: ByteArray)

    // Listener
    fun setListener(listener: BleTransportListener?)
}

interface BleTransportListener {
    fun onConnectionStateChanged(connected: Boolean)
    fun onServicesDiscovered(success: Boolean)
    fun onDescriptorWritten()
    fun onCharacteristicChanged(data: ByteArray)
    fun onCharacteristicWritten()
}
