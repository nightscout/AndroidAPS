package app.aaps.pump.aaruy.emulator

import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.concurrent.withLock
import app.aaps.core.interfaces.pump.ble.Advertisement
import app.aaps.core.interfaces.pump.ble.BleFailure
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
import app.aaps.core.interfaces.pump.emulator.EmulatedPumpState
import app.aaps.core.interfaces.pump.emulator.EmulatedTempBasal
import app.aaps.core.interfaces.pump.emulator.EmulatorSpeed
import app.aaps.core.interfaces.pump.emulator.PumpEmulatorControl
import app.aaps.pump.aaruy.comm.AaruyBleClient
import kotlin.concurrent.Volatile
import kotlin.math.roundToInt
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The radio of [AaruyPumpEmulator], on the Bluetooth v2 [EmulatedBleCentral]. Only the radio is emulated: the driver's
 * client, its frames and the link are the ones a real pump gets.
 *
 * Radio-like delays, because the driver has windows that real delays can break: a write is done for the phone first,
 * and the pump answers later, one frame about every 100 ms. The emulator is not thread safe, so every use of it is under
 * one lock: the tick, a frame from the phone, and a test hook.
 */
class AaruyEmulatorPeripheral(val emulator: AaruyPumpEmulator) : EmulatedPeripheral, PumpEmulatorControl {

    private val lock = AapsLock()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clockStart = TimeSource.Monotonic.markNow()
    private fun now(): Long = clockStart.elapsedNow().inWholeMilliseconds

    /** When the pump can send its next frame. Under [lock]. */
    private var nextFrameAt = 0L

    /** The link that is up, or null. A new connect makes a new one; the old one is dead then. */
    @Volatile private var link: PeripheralLink? = null

    private val written = mutableListOf<ByteArray>()

    /** Every frame written by the app, in order. */
    val writes: List<ByteArray> get() = lock.withLock { written.toList() }

    /** The pump does not answer a connect, as when it is out of range. */
    @Volatile var outOfRange = false

    /** The pump does the next command with this parameter, then the link is lost before the answer. */
    @Volatile var loseAnswerToParam = -1

    /** How often the phone bonded with the pump, with the right PIN. */
    @Volatile var bonds = 0
        private set

    init {
        scope.launch {
            while (isActive) {
                delay(TICK_MS)
                lock.withLock { deliver(emulator.tick()) }
            }
        }
    }

    override val device: DeviceId = DeviceId(DEVICE_ADDRESS)

    override val services = GattServices(
        mapOf(
            AaruyBleClient.CHARACTERISTIC_WRITE to setOf(CharacteristicProperty.WRITE_WITHOUT_RESPONSE),
            AaruyBleClient.CHARACTERISTIC_NOTIFY to setOf(CharacteristicProperty.NOTIFY),
            AaruyBleClient.CHARACTERISTIC_FIRMWARE to setOf(CharacteristicProperty.READ)
        )
    )

    override fun advertisement(): Advertisement? =
        if (outOfRange) null
        else Advertisement(device, DEVICE_NAME, EMULATED_RSSI, listOf(AaruyBleClient.SERVICE_PUMP), emptyMap(), emptyMap())

    override suspend fun onConnect(link: PeripheralLink): Boolean {
        if (outOfRange) return false
        delay(CONNECT_MS)
        if (outOfRange) return false
        this.link = link
        return true
    }

    /** A real pump asks for the PIN that comes from its name, and the user presses its button. The emulator needs no button. */
    override suspend fun onBond(pin: String?): BleResult<Unit> {
        if (pin != AaruyBleClient.pinFromDeviceName(DEVICE_NAME)) return BleResult.Failed(BleFailure(BleFailure.Kind.REFUSED, message = "wrong PIN"))
        bonds++
        return BleResult.Ok(Unit)
    }

    override suspend fun onRead(link: PeripheralLink, characteristic: CharacteristicId): BleResult<ByteArray> =
        if (characteristic == AaruyBleClient.CHARACTERISTIC_FIRMWARE) BleResult.Ok(FIRMWARE.encodeToByteArray())
        else BleResult.Failed(BleFailure(BleFailure.Kind.NOT_SUPPORTED, message = "the emulator does not read $characteristic"))

    override suspend fun onWrite(link: PeripheralLink, characteristic: CharacteristicId, data: ByteArray, type: WriteType): BleResult<Unit> {
        delay(RADIO_MS)
        lock.withLock { written += data.copyOf() }
        scope.launch {
            delay(PUMP_ANSWER_MS)
            lock.withLock { answer(data) }
        }
        return BleResult.Ok(Unit)
    }

    override fun onDisconnect(link: PeripheralLink) {
        if (this.link === link) this.link = null
    }

    /** Under [lock]. */
    private fun answer(data: ByteArray) {
        val (param, action) = emulator.peek(data) ?: (-1 to null)
        lostCommands.firstOrNull { emulator.isCommand(it, param, action) }?.let {
            // The pump never got it: it does nothing and sends nothing.
            lostCommands.remove(it)
            return
        }
        val answer = emulator.handle(data)
        lostAnswers.firstOrNull { emulator.isCommand(it, param, action) }?.let {
            // The pump did it, and its answer is lost. The link stays.
            lostAnswers.remove(it)
            return
        }
        if (emulator.lastParamId == loseAnswerToParam) {
            // The pump did it, and the answer never comes.
            loseAnswerToParam = -1
            outOfRange = true
            loseConnection()
            return
        }
        val answerDelay = emulator.answerDelayMs
        emulator.answerDelayMs = 0
        scope.launch {
            delay(answerDelay)
            lock.withLock { deliver(answer) }
        }
        if (emulator.unbindRequested) {
            emulator.unbindRequested = false
            scope.launch {
                delay(UNBIND_DROP_MS)
                loseConnection()
            }
        }
    }

    /** Seen on a real pump: it sends one frame about every 100 ms, so all its settings take close to a second. Under [lock]. */
    private fun deliver(messages: List<ByteArray>) {
        messages.forEach { message ->
            val now = now()
            val at = maxOf(now, nextFrameAt)
            nextFrameAt = at + FRAME_GAP_MS
            scope.launch {
                delay(at - now)
                link?.send(AaruyBleClient.CHARACTERISTIC_NOTIFY, message)
            }
        }
    }

    /** The link is lost without the app asking. */
    fun loseConnection() {
        link?.drop()
    }

    /** Runs [block] with the emulator to itself: its tick overwrites a value set from another thread. */
    fun onPumpThread(block: () -> Unit) = lock.withLock(block)

    /** Sends a message from the pump now, for example an alarm. */
    fun pumpSends(message: ByteArray) = lock.withLock { deliver(listOf(message)) }

    /** Stops the tick. For a test that ends. */
    fun shutdown() = scope.cancel()

    // ---- PumpEmulatorControl ----

    /** Faults for the next command of each kind. Under [lock]. */
    private val lostCommands = ArrayList<EmulatedCommand>()
    private val lostAnswers = ArrayList<EmulatedCommand>()

    override var speed: EmulatorSpeed
        get() = emulator.speed
        set(value) {
            emulator.speed = value
        }

    override fun breakLink(): Boolean {
        loseConnection()
        return true
    }

    override fun setOutOfRange(outOfRange: Boolean): Boolean {
        this.outOfRange = outOfRange
        if (outOfRange) loseConnection()
        return true
    }

    override fun loseCommand(command: EmulatedCommand): Boolean = lock.withLock { lostCommands.add(command) }

    override fun loseAnswer(command: EmulatedCommand): Boolean = lock.withLock { lostAnswers.add(command) }

    override fun raise(alarm: EmulatedAlarm): Boolean = lock.withLock { emulator.raise(alarm)?.also { deliver(it) } != null }

    override fun stopNextBolusAt(units: Double): Boolean = lock.withLock {
        emulator.stopNextBolusAt = (units * 1000).roundToInt()
        true
    }

    /** A new pump in reach: nothing of the old one runs, and no fault waits for it. */
    override fun replaceWithNewPump(): Boolean {
        lock.withLock {
            emulator.replaceWithNewPump()
            lostCommands.clear()
            lostAnswers.clear()
        }
        loseAnswerToParam = -1
        loseConnection()
        outOfRange = false
        return true
    }

    override fun state(): EmulatedPumpState = lock.withLock {
        EmulatedPumpState(
            boluses = emulator.boluses,
            bolusRunning = emulator.bolusRunning,
            tempBasal = emulator.tempBasalRate?.let { EmulatedTempBasal(absolute = it / 1000.0, percent = null) },
            suspended = emulator.runningStatus !in AaruyPumpEmulator.STATUS_BASAL..AaruyPumpEmulator.STATUS_BOLUS,
            reservoir = emulator.reservoir,
            linkUp = link?.isUp == true,
            commands = emulator.commands
        )
    }

    /** The pump, not the radio: after the app start a new peripheral connects to it. */
    override fun saveState(): ByteArray = lock.withLock { emulator.saveState() }

    companion object {

        const val DEVICE_NAME = "aaruy_Q12345"
        const val DEVICE_ADDRESS = "00:11:22:33:44:55"

        private const val FIRMWARE = "emulator"
        private const val EMULATED_RSSI = -40
        private const val TICK_MS = 200L
        private const val CONNECT_MS = 200L
        private const val RADIO_MS = 15L
        private const val PUMP_ANSWER_MS = 60L
        private const val FRAME_GAP_MS = 100L
        private const val UNBIND_DROP_MS = 2_000L
    }
}
