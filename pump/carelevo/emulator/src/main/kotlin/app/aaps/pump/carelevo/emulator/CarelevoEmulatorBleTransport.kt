package app.aaps.pump.carelevo.emulator

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.ble.BleAdapter
import app.aaps.core.interfaces.pump.ble.BleGatt
import app.aaps.core.interfaces.pump.ble.BleScanner
import app.aaps.core.interfaces.pump.ble.BleTransportListener
import app.aaps.core.interfaces.pump.ble.PairingState
import app.aaps.core.interfaces.pump.ble.ScannedDevice
import app.aaps.core.interfaces.pump.emulator.EmulatedAlarm
import app.aaps.core.interfaces.pump.emulator.EmulatedCommand
import app.aaps.core.interfaces.pump.emulator.EmulatedExtendedBolus
import app.aaps.core.interfaces.pump.emulator.EmulatedPumpState
import app.aaps.core.interfaces.pump.emulator.EmulatedTempBasal
import app.aaps.core.interfaces.pump.emulator.EmulatorSpeed
import app.aaps.core.interfaces.pump.emulator.PumpEmulatorControl
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
) : CarelevoBleTransport, PumpEmulatorControl {

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
            // ScannedDevice.rssi defaults to Int.MIN_VALUE — CarelevoPatchConnectViewModel's discovery
            // scan filters out anything below MIN_SCAN_RSSI (-45), so an emulated device reported at
            // the default would silently never be selectable. A strong, fixed reading stands in for
            // "right next to the phone", which is what emulation is meant to simulate.
            _scannedDevices.tryEmit(ScannedDevice(name = deviceName(), address = address, rssi = EMULATED_RSSI))
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
            val opcode = data.firstOrNull() ?: return
            if (takeFault(lostCommands, opcode)) {
                aapsLogger?.debug(LTag.PUMPEMULATOR, "emulator: command 0x%02X lost before the patch".format(opcode))
                return
            }
            val answers = emulator.handle(data)
            if (takeFault(lostAnswers, opcode)) {
                aapsLogger?.debug(LTag.PUMPEMULATOR, "emulator: answer to 0x%02X lost".format(opcode))
                return
            }
            answers.forEach { listener?.onCharacteristicChanged(it) }
        }
    }

    // ---- PumpEmulatorControl ----------------------------------------------------------------

    /**
     * The opcodes of each fault, in the order the test set them. Each fault is used once, and only the first one can
     * fire. A fault named for a command with two opcodes (the basal program) is used up by either.
     */
    private val lostCommands = mutableListOf<Set<Byte>>()
    private val lostAnswers = mutableListOf<Set<Byte>>()

    private fun takeFault(faults: MutableList<Set<Byte>>, opcode: Byte): Boolean = synchronized(faults) {
        if (faults.firstOrNull()?.contains(opcode) == true) {
            faults.removeAt(0)
            true
        } else false
    }

    private fun addFault(faults: MutableList<Set<Byte>>, command: EmulatedCommand): Boolean = synchronized(faults) {
        faults.add(opcodesOf(command))
    }

    override var speed: EmulatorSpeed
        get() = emulator.speed
        set(value) {
            emulator.speed = value
        }

    override fun breakLink(): Boolean {
        gatt.disconnect()
        return true
    }

    override fun setOutOfRange(outOfRange: Boolean): Boolean {
        // A real stack starts the connect and the patch never answers, so the connect times out.
        reportConnected = !outOfRange
        if (outOfRange) gatt.disconnect()
        return true
    }

    override fun loseCommand(command: EmulatedCommand): Boolean = addFault(lostCommands, command)

    override fun loseAnswer(command: EmulatedCommand): Boolean = addFault(lostAnswers, command)

    /**
     * UNVERIFIED which tier a real patch uses for each. The driver reads them from the 0x43 active-alarm snapshot. A low
     * reservoir and a long stop are no snapshot flags: the driver works them out from the reported values.
     */
    override fun raise(alarm: EmulatedAlarm): Boolean {
        val state = emulator.state
        when (alarm) {
            EmulatedAlarm.EMPTY_RESERVOIR  -> state.criticalAlarmFlags = state.criticalAlarmFlags.copy(outOfInsulin = true)
            EmulatedAlarm.OCCLUSION        -> state.criticalAlarmFlags = state.criticalAlarmFlags.copy(occlusionDetected = true)
            EmulatedAlarm.LOW_BATTERY      -> state.advisoryAlarmFlags = state.advisoryAlarmFlags.copy(lowBattery = true)
            EmulatedAlarm.LOW_RESERVOIR,
            EmulatedAlarm.STOPPED_TOO_LONG -> return false
        }
        return true
    }

    override fun stopNextBolusAt(units: Double): Boolean {
        emulator.stopNextBolusAt = units
        return true
    }

    override fun state(): EmulatedPumpState {
        val state = emulator.state
        return EmulatedPumpState(
            boluses = emulator.boluses,
            bolusRunning = emulator.bolusRunning,
            tempBasal = if (state.tempBasalRunning) EmulatedTempBasal(state.tempBasalAbsolute, state.tempBasalPercent) else null,
            suspended = state.stopped,
            reservoir = emulator.reservoir,
            linkUp = connected,
            commands = emulator.commands,
            extendedBolus = if (state.extendedBolusRunning) EmulatedExtendedBolus(state.extendedBolusUnits, state.extendedBolusMinutes) else null
        )
    }

    /** The patch, not the radio: after the app start a new transport connects to it. */
    override fun saveState(): ByteArray = emulator.saveState()

    private fun opcodesOf(command: EmulatedCommand): Set<Byte> = when (command) {
        EmulatedCommand.BOLUS_START      -> setOf(CarelevoPumpEmulator.OP_IMMEDIATE_BOLUS)
        EmulatedCommand.BOLUS_STOP       -> setOf(CarelevoPumpEmulator.OP_BOLUS_CANCEL)
        EmulatedCommand.TEMP_BASAL_START -> setOf(CarelevoPumpEmulator.OP_TEMP_BASAL)
        EmulatedCommand.TEMP_BASAL_STOP  -> setOf(CarelevoPumpEmulator.OP_TEMP_BASAL_CANCEL)
        EmulatedCommand.BASAL_PROFILE    -> BASAL_PROGRAM_OPCODES
        EmulatedCommand.STATUS           -> setOf(CarelevoPumpEmulator.OP_INFUSION_INFO)
    }

    private fun deviceName(): String = "$DEVICE_NAME_PREFIX${pumpState.serialNumber}"

    private fun macAddressString(): String =
        pumpState.macAddress.joinToString(separator = ":") { "%02X".format(it) }

    private companion object {

        const val DEVICE_NAME_PREFIX = "CareLevo-"

        /** Well above CarelevoPatchConnectViewModel.MIN_SCAN_RSSI (-45) — "right next to the phone". */
        const val EMULATED_RSSI = -30

        /** The basal program is set at activation and updated later, with two opcodes. */
        val BASAL_PROGRAM_OPCODES = setOf(CarelevoPumpEmulator.OP_BASAL_SET, CarelevoPumpEmulator.OP_BASAL_UPDATE)
    }
}
