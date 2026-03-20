package app.aaps.pump.danars.services

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

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

    /** Current pairing/handshake state, updated by BLEComm during connection. */
    val pairingState: StateFlow<PairingState>

    /** Update pairing state (called by BLEComm). */
    fun updatePairingState(state: PairingState)

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

    /** Flow of discovered devices during scanning. */
    val scannedDevices: SharedFlow<ScannedDevice>
    fun startScan()
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

/**
 * Pairing/handshake progress state exposed to UI.
 */
enum class PairingStep {

    IDLE,
    CONNECTING,
    HANDSHAKE_IN_PROGRESS,
    WAITING_FOR_PAIRING_CONFIRM,  // v1: pump displaying pairing request
    WAITING_FOR_PASSWORD,         // v1: user password doesn't match pump
    WAITING_FOR_PIN,              // RSv3: need PIN codes from pump display
    CONNECTED,
    ERROR
}

data class PairingState(
    val step: PairingStep = PairingStep.IDLE,
    val errorMessage: String? = null
)
