package app.aaps.pump.aaruy.emulator

import app.aaps.core.interfaces.pump.ble.BleAdapter
import app.aaps.core.interfaces.pump.ble.BleGatt
import app.aaps.core.interfaces.pump.ble.BleScanner
import app.aaps.core.interfaces.pump.ble.BleTransportListener
import app.aaps.core.interfaces.pump.ble.PairingState
import app.aaps.core.interfaces.pump.ble.ScannedDevice
import app.aaps.pump.aaruy.comm.AaruyBleTransport
import app.aaps.pump.aaruy.comm.AaruyTransportExtras
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * The radio side of [AaruyPumpEmulator]. Everything runs on one thread with radio-like delays: a write is
 * confirmed first and the pump answers later, because the driver has windows that real delays can break.
 */
class AaruyEmulatorBleTransport(val emulator: AaruyPumpEmulator) : AaruyBleTransport {

    private val thread: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { Thread(it, "AaruyEmulator").apply { isDaemon = true } }

    @Volatile private var listener: BleTransportListener? = null
    @Volatile private var connected = false
    @Volatile private var extras: AaruyTransportExtras? = null
    private var nextFrameAt = 0L

    /** Every frame written by the app, in order. */
    val writes: MutableList<ByteArray> = java.util.Collections.synchronizedList(ArrayList())

    /** `connect()` returns true, but the pump never answers, as when it is out of range. */
    @Volatile var outOfRange = false

    init {
        thread.scheduleWithFixedDelay({ tick() }, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS)
    }

    private fun tick() {
        try {
            deliver(emulator.tick())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Seen on a real pump: it sends one frame about every 100 ms, so all its settings take close to a second. */
    private fun deliver(messages: List<ByteArray>) {
        messages.forEach { message ->
            val now = System.currentTimeMillis()
            val at = maxOf(now, nextFrameAt)
            nextFrameAt = at + FRAME_GAP_MS
            later(at - now) { if (connected) listener?.onCharacteristicChanged(message) }
        }
    }

    private fun later(delayMs: Long, block: () -> Unit) {
        thread.schedule(block, delayMs, TimeUnit.MILLISECONDS)
    }

    /** The link is lost without the app asking. */
    fun loseConnection() {
        later(0) {
            if (connected) {
                connected = false
                listener?.onConnectionStateChanged(false)
            }
        }
    }

    /** Runs on the thread that owns the emulator. The emulator is not thread safe. */
    fun onPumpThread(block: () -> Unit) {
        later(0, block)
    }

    /** Sends a message from the pump now, for example an alarm. */
    fun pumpSends(message: ByteArray) {
        later(0) { deliver(listOf(message)) }
    }

    override val adapter: BleAdapter = object : BleAdapter {
        override fun enable() {}
        override fun getDeviceName(address: String): String = DEVICE_NAME
        override fun isDeviceBonded(address: String): Boolean = true
        override fun createBond(address: String): Boolean = true
        override fun removeBond(address: String) {}
    }

    private val devices = MutableSharedFlow<ScannedDevice>(replay = 1, extraBufferCapacity = 10)

    override val scanner: BleScanner = object : BleScanner {
        override val scannedDevices: SharedFlow<ScannedDevice> = devices
        override fun startScan() {
            devices.tryEmit(ScannedDevice(DEVICE_NAME, DEVICE_ADDRESS, rssi = EMULATED_RSSI))
        }

        override fun stopScan() {}
    }

    override val gatt: BleGatt = object : BleGatt {
        override fun connect(address: String): Boolean {
            later(CONNECT_MS) {
                if (!outOfRange) {
                    connected = true
                    listener?.onConnectionStateChanged(true)
                }
            }
            return true
        }

        override fun disconnect() {
            later(RADIO_MS) {
                connected = false
                listener?.onConnectionStateChanged(false)
            }
        }

        override fun close() {
            connected = false
        }

        override fun discoverServices() {
            later(DISCOVER_MS) { listener?.onServicesDiscovered(true) }
        }

        override fun findCharacteristics(): Boolean = true

        override fun enableNotifications() {
            later(RADIO_MS) { listener?.onDescriptorWritten() }
        }

        override fun writeCharacteristic(data: ByteArray) {
            writes.add(data)
            later(RADIO_MS) {
                listener?.onCharacteristicWritten()
                later(PUMP_ANSWER_MS) {
                    val answer = emulator.handle(data)
                    later(emulator.answerDelayMs) { deliver(answer) }
                    emulator.answerDelayMs = 0
                    if (emulator.unbindRequested) {
                        emulator.unbindRequested = false
                        later(UNBIND_DROP_MS) { loseConnection() }
                    }
                }
            }
        }
    }

    private val state = MutableStateFlow(PairingState())
    override val pairingState: StateFlow<PairingState> = state

    override fun updatePairingState(state: PairingState) {
        this.state.value = state
    }

    override fun setExtrasListener(listener: AaruyTransportExtras?) {
        extras = listener
    }

    override fun requestMtu(mtu: Int) {
        later(RADIO_MS) { extras?.onMtuChanged(mtu) }
    }

    override fun readFirmwareVersion() {
        later(RADIO_MS) { extras?.onFirmwareVersionRead("emulator".toByteArray()) }
    }

    override fun setListener(listener: BleTransportListener?) {
        this.listener = listener
    }

    fun shutdown() {
        thread.shutdownNow()
    }

    companion object {

        const val DEVICE_NAME = "aaruy_Q12345"
        const val DEVICE_ADDRESS = "00:11:22:33:44:55"

        private const val EMULATED_RSSI = -40
        private const val TICK_MS = 200L
        private const val CONNECT_MS = 200L
        private const val DISCOVER_MS = 100L
        private const val RADIO_MS = 15L
        private const val PUMP_ANSWER_MS = 60L
        private const val FRAME_GAP_MS = 100L
        private const val UNBIND_DROP_MS = 2_000L
    }
}
