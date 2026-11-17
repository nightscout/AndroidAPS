package app.aaps.pump.carelevo.emulator

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.ble.Advertisement
import app.aaps.core.interfaces.pump.ble.BleResult
import app.aaps.core.interfaces.pump.ble.CharacteristicId
import app.aaps.core.interfaces.pump.ble.CharacteristicProperty
import app.aaps.core.interfaces.pump.ble.DeviceId
import app.aaps.core.interfaces.pump.ble.EmulatedBleCentral
import app.aaps.core.interfaces.pump.ble.EmulatedPeripheral
import app.aaps.core.interfaces.pump.ble.GattServices
import app.aaps.core.interfaces.pump.ble.PeripheralLink
import app.aaps.core.interfaces.pump.ble.WriteType
import app.aaps.core.interfaces.pump.emulator.EmulatedAlarm
import app.aaps.core.interfaces.pump.emulator.EmulatedCommand
import app.aaps.core.interfaces.pump.emulator.EmulatedExtendedBolus
import app.aaps.core.interfaces.pump.emulator.EmulatedPumpState
import app.aaps.core.interfaces.pump.emulator.EmulatedTempBasal
import app.aaps.core.interfaces.pump.emulator.EmulatorSpeed
import app.aaps.core.interfaces.pump.emulator.PumpEmulatorControl
import app.aaps.pump.carelevo.ble.CarelevoGatt
import java.util.Collections

/**
 * The radio of an emulated CareLevo patch, on [EmulatedBleCentral]: the driver's writes go to [emulator], and its
 * answers come back as notifications on [CarelevoGatt.NOTIFY]. Only the radio is emulated. The driver's session, its
 * client and the protocol run for real, and the link is the same `QueuedBleLink` a real patch gets.
 *
 * Each write is answered at once, inside the write. That is safe: the driver registers its waiter before it writes,
 * and the link keeps data until the driver collects it.
 */
class CarelevoEmulatorPeripheral(
    val emulator: CarelevoPumpEmulator = CarelevoPumpEmulator(),
    private val aapsLogger: AAPSLogger? = null
) : EmulatedPeripheral, PumpEmulatorControl {

    val pumpState: CarelevoPumpState get() = emulator.state

    /** Every frame the driver wrote, in order: what a test asserts on. */
    val writes: MutableList<ByteArray> = Collections.synchronizedList(mutableListOf())

    /** How often the phone bonded with the patch. */
    @Volatile var bondCalls: Int = 0
        private set

    /** False: the patch does not answer a connect, so the connect waits until its time limit. */
    @Volatile var reportConnected: Boolean = true

    /** The link that is up, or null. A new connect makes a new one; the old one is dead then. */
    @Volatile private var link: PeripheralLink? = null

    override val device: DeviceId get() = DeviceId(pumpState.macAddress.joinToString(separator = ":") { "%02X".format(it) })

    override val services: GattServices = GattServices(
        mapOf(
            CarelevoGatt.WRITE to setOf(CharacteristicProperty.WRITE, CharacteristicProperty.WRITE_WITHOUT_RESPONSE),
            CarelevoGatt.NOTIFY to setOf(CharacteristicProperty.NOTIFY)
        )
    )

    // ---- EmulatedPeripheral -----------------------------------------------------------------

    /**
     * Out of range a patch is not seen. Its rssi stands for "next to the phone": the pairing wizard takes no patch
     * weaker than -45.
     */
    override fun advertisement(): Advertisement? =
        if (!reportConnected) null
        else Advertisement(device, "$DEVICE_NAME_PREFIX${pumpState.serialNumber}", EMULATED_RSSI, listOf(CarelevoGatt.SERVICE), emptyMap(), emptyMap())

    override suspend fun onConnect(link: PeripheralLink): Boolean {
        if (!reportConnected) return false
        this.link = link
        return true
    }

    override suspend fun onBond(): BleResult<Unit> {
        bondCalls++
        return BleResult.Ok(Unit)
    }

    override suspend fun onWrite(link: PeripheralLink, characteristic: CharacteristicId, data: ByteArray, type: WriteType): BleResult<Unit> {
        writes += data.copyOf()
        val opcode = data.firstOrNull() ?: return BleResult.Ok(Unit)
        // The write itself reached the radio; only the patch then does nothing, or its answer is lost.
        if (takeFault(lostCommands, opcode)) {
            aapsLogger?.debug(LTag.PUMPEMULATOR, "emulator: command 0x%02X lost before the patch".format(opcode))
            return BleResult.Ok(Unit)
        }
        val answers = emulator.handle(data)
        if (takeFault(lostAnswers, opcode)) {
            aapsLogger?.debug(LTag.PUMPEMULATOR, "emulator: answer to 0x%02X lost".format(opcode))
            return BleResult.Ok(Unit)
        }
        answers.forEach { link.send(CarelevoGatt.NOTIFY, it) }
        return BleResult.Ok(Unit)
    }

    override fun onDisconnect(link: PeripheralLink) {
        if (this.link === link) this.link = null
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
        link?.drop()
        return true
    }

    override fun setOutOfRange(outOfRange: Boolean): Boolean {
        // A real stack starts the connect and the patch never answers, so the connect times out.
        reportConnected = !outOfRange
        if (outOfRange) link?.drop()
        return true
    }

    override fun loseCommand(command: EmulatedCommand): Boolean = addFault(lostCommands, command)

    override fun loseAnswer(command: EmulatedCommand): Boolean = addFault(lostAnswers, command)

    /** A new patch, in range. Only between connections: the old patch is out of range or its link is closed. */
    override fun replaceWithNewPump(): Boolean {
        link?.drop()
        emulator.replaceWithNewPatch()
        synchronized(lostCommands) { lostCommands.clear() }
        synchronized(lostAnswers) { lostAnswers.clear() }
        reportConnected = true
        return true
    }

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
            linkUp = link?.isUp == true,
            commands = emulator.commands,
            extendedBolus = if (state.extendedBolusRunning) EmulatedExtendedBolus(state.extendedBolusUnits, state.extendedBolusMinutes) else null,
            // A patch tells no battery level, only that it runs low.
            battery = null,
            // 24 hourly segments in three parts; only a whole program is one.
            basal = state.basalProgram.takeIf { it.size == HOURS_PER_DAY }
        )
    }

    /** The patch, not the radio: after the app start a new radio connects to it. */
    override fun saveState(): ByteArray = emulator.saveState()

    private fun opcodesOf(command: EmulatedCommand): Set<Byte> = when (command) {
        EmulatedCommand.BOLUS_START          -> setOf(CarelevoPumpEmulator.OP_IMMEDIATE_BOLUS)
        EmulatedCommand.BOLUS_STOP           -> setOf(CarelevoPumpEmulator.OP_BOLUS_CANCEL)
        EmulatedCommand.TEMP_BASAL_START     -> setOf(CarelevoPumpEmulator.OP_TEMP_BASAL)
        EmulatedCommand.TEMP_BASAL_STOP      -> setOf(CarelevoPumpEmulator.OP_TEMP_BASAL_CANCEL)
        EmulatedCommand.EXTENDED_BOLUS_START -> setOf(CarelevoPumpEmulator.OP_EXTEND_BOLUS)
        EmulatedCommand.EXTENDED_BOLUS_STOP  -> setOf(CarelevoPumpEmulator.OP_EXTEND_BOLUS_CANCEL)
        EmulatedCommand.BASAL_PROFILE        -> BASAL_PROGRAM_OPCODES
        EmulatedCommand.STATUS               -> setOf(CarelevoPumpEmulator.OP_INFUSION_INFO)
    }

    private companion object {

        const val DEVICE_NAME_PREFIX = "CareLevo-"
        const val HOURS_PER_DAY = 24

        /** Well above CarelevoPatchConnectViewModel.MIN_SCAN_RSSI (-45): "right next to the phone". */
        const val EMULATED_RSSI = -30

        /** The basal program is set at activation and updated later, with two opcodes. */
        val BASAL_PROGRAM_OPCODES = setOf(CarelevoPumpEmulator.OP_BASAL_SET, CarelevoPumpEmulator.OP_BASAL_UPDATE)
    }
}
