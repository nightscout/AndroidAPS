package app.aaps.pump.carelevo.emulator

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.ble.BleAdapter
import app.aaps.core.interfaces.pump.ble.BleGatt
import app.aaps.core.interfaces.pump.ble.BleScanner
import app.aaps.core.interfaces.pump.ble.BleTransportListener
import app.aaps.core.interfaces.pump.ble.PairingState
import app.aaps.core.interfaces.pump.ble.ScannedDevice
import app.aaps.pump.carelevo.ble.CarelevoBleTransport
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * [CarelevoBleTransport] that talks to a [CarelevoPumpEmulator] instead of real Bluetooth, so the
 * whole CareLevo driver — sessions, pairing, commands, coordinators — runs without patch hardware.
 *
 * The seam is narrow: the driver never touches this directly. `BleTransportGattConnection` adapts it
 * to a `GattConnection` and `BleClientImpl` does opcode correlation above that, so everything the
 * protocol actually cares about still runs for real. Only the radio is fake.
 *
 * Each write is answered synchronously on the calling thread, which keeps tests deterministic: by the
 * time `writeCharacteristic` returns, the notifications have already been delivered. That is safe
 * because `BleClientImpl` registers its waiter *before* writing, so an instantaneous reply cannot race.
 *
 * The driver opens a fresh session per operation, so `connect`/`close` and `setListener` are exercised
 * repeatedly; this tolerates the register → close → register cycle rather than warning about it.
 */
class CarelevoEmulatorBleTransport(
    val emulator: CarelevoPumpEmulator = CarelevoPumpEmulator(),
    private val aapsLogger: AAPSLogger? = null,
    /** Serial reported by a discovery scan. Defaults to whatever the emulated patch already carries. */
    private val serialNumberProvider: (() -> String)? = null
) : CarelevoBleTransport {

    private var listener: BleTransportListener? = null

    @Volatile private var connected = false

    val pumpState: CarelevoPumpState get() = emulator.state

    /** Every frame the driver has written, in order — the assertion surface for tests. */
    val writes: MutableList<ByteArray> = mutableListOf()

    /** Bond state of the emulated patch; flip to drive the driver's bonding paths. */
    var bonded: Boolean = true
    var bondCalls: Int = 0

    /** `connect()` returns false, as if the BLE stack refused to start the connection. */
    var connectRefused: Boolean = false

    /** `connect()` succeeds but the patch never reports CONNECTED — drives the connect timeout. */
    var reportConnected: Boolean = true

    override var scanAddress: String? = null
    override var onGattError133: (() -> Unit)? = null

    override val adapter: BleAdapter = EmulatorAdapter()
    override val scanner: BleScanner = EmulatorScanner()
    override val gatt: BleGatt = EmulatorGatt()

    private val _pairingState = MutableStateFlow(PairingState())
    override val pairingState: StateFlow<PairingState> = _pairingState

    override fun updatePairingState(state: PairingState) {
        _pairingState.value = state
    }

    override fun setListener(listener: BleTransportListener?) {
        this.listener = listener
    }

    // ---- BleAdapter -------------------------------------------------------------------------

    private inner class EmulatorAdapter : BleAdapter {

        override fun enable() {}
        override fun getDeviceName(address: String): String = deviceName()
        override fun isDeviceBonded(address: String): Boolean = bonded
        override fun createBond(address: String): Boolean {
            bondCalls++
            bonded = true
            return true
        }

        override fun removeBond(address: String) {
            bonded = false
        }
    }

    // ---- BleScanner -------------------------------------------------------------------------

    private inner class EmulatorScanner : BleScanner {

        private val _scannedDevices = MutableSharedFlow<ScannedDevice>(replay = 1, extraBufferCapacity = 10)
        override val scannedDevices: SharedFlow<ScannedDevice> = _scannedDevices

        override fun startScan() {
            // Discovery vs reconnect is decided by isNullOrEmpty(), matching CarelevoBleTransportImpl —
            // a null-only check would treat an empty filter as a reconnect and report a blank address.
            val filter = scanAddress?.ifEmpty { null }
            // A discovery scan (no address filter) is where a fresh patch gets its serial.
            if (filter == null) serialNumberProvider?.invoke()?.let { pumpState.serialNumber = it }
            val address = filter ?: macAddressString()
            aapsLogger?.debug(LTag.PUMPEMULATOR, "emulator: scan reporting $address")
            _scannedDevices.tryEmit(ScannedDevice(name = deviceName(), address = address))
        }

        override fun stopScan() {}
    }

    // ---- BleGatt ----------------------------------------------------------------------------

    private inner class EmulatorGatt : BleGatt {

        override fun connect(address: String): Boolean {
            if (connectRefused) return false
            connected = true
            if (reportConnected) listener?.onConnectionStateChanged(true)
            return true
        }

        override fun disconnect() {
            if (connected) {
                connected = false
                listener?.onConnectionStateChanged(false)
            }
        }

        override fun close() {
            connected = false
        }

        override fun discoverServices() {
            listener?.onServicesDiscovered(true)
        }

        override fun findCharacteristics(): Boolean = true

        override fun enableNotifications() {
            listener?.onDescriptorWritten()
        }

        override fun writeCharacteristic(data: ByteArray) {
            writes += data
            listener?.onCharacteristicWritten()
            emulator.handle(data).forEach { listener?.onCharacteristicChanged(it) }
        }
    }

    private fun deviceName(): String = "$DEVICE_NAME_PREFIX${pumpState.serialNumber}"

    private fun macAddressString(): String =
        pumpState.macAddress.joinToString(separator = ":") { "%02X".format(it) }

    private companion object {

        const val DEVICE_NAME_PREFIX = "CareLevo-"
    }
}
