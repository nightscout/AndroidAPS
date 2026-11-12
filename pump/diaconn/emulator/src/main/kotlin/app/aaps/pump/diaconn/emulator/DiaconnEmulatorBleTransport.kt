package app.aaps.pump.diaconn.emulator

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.ble.BleAdapter
import app.aaps.core.interfaces.pump.ble.BleGatt
import app.aaps.core.interfaces.pump.ble.BleScanner
import app.aaps.core.interfaces.pump.ble.BleTransport
import app.aaps.core.interfaces.pump.ble.BleTransportListener
import app.aaps.core.interfaces.pump.ble.PairingState
import app.aaps.core.interfaces.pump.ble.ScannedDevice
import app.aaps.core.interfaces.pump.emulator.EmulatedAlarm
import app.aaps.core.interfaces.pump.emulator.EmulatedCommand
import app.aaps.core.interfaces.pump.emulator.EmulatedPumpState
import app.aaps.core.interfaces.pump.emulator.EmulatorSpeed
import app.aaps.core.interfaces.pump.emulator.PumpEmulatorControl
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * The Bluetooth link to an emulated Diaconn G8: the driver writes its 20-byte requests here and gets the answers and the
 * reports of [emulator] as notifications, one packet (20 or 182 bytes) each.
 *
 * Threads, as on a real radio: the connect comes up a moment after `connect` returned, on its own thread. Service
 * discovery and notifications answer at once, on the thread that asks. Each answer and report goes out on one sender
 * thread, in the order the pump made them, each after its delay. So an answer goes out before any report the same
 * request caused, as on the real pump. A link has a number: what was meant for an older link is dropped.
 */
class DiaconnEmulatorBleTransport(
    val emulator: DiaconnPumpEmulator = DiaconnPumpEmulator(),
    private val aapsLogger: AAPSLogger? = null
) : BleTransport, PumpEmulatorControl {

    val pumpState: DiaconnPumpState get() = emulator.state

    @Volatile private var listener: BleTransportListener? = null
    @Volatile private var connected = false

    /** The driver switched the notifications on for this link. A real pump sends nothing before. */
    @Volatile private var notifying = false

    /** While true, a connect never comes up. */
    @Volatile private var outOfRange = false
    @Volatile private var linkGeneration = 0

    /** Held while a request is handled and its answer queued, and while a report is queued: it keeps their order. */
    private val sendLock = Any()

    /** Alarms raised while no link was up, sent when the next one is. Under [sendLock]. */
    private val heldAlarms = ArrayList<ByteArray>()

    /** One packet after another, each not before its time. Its one thread ends when it has nothing to do. */
    private val sender = ThreadPoolExecutor(0, 1, 1, TimeUnit.SECONDS, LinkedBlockingQueue()) { runnable ->
        Thread(runnable, "DiaconnEmulatorSender").apply { isDaemon = true }
    }

    private val pendingThreads = mutableListOf<Thread>()

    init {
        emulator.reportSink = { report -> sendReport(report) }
    }

    // --- PumpEmulatorControl ---

    /** How long a bolus and an answer take. */
    override var speed: EmulatorSpeed
        get() = emulator.speed
        set(value) {
            emulator.speed = value
        }

    override fun breakLink(): Boolean {
        if (dropLink()) listener?.onConnectionStateChanged(false)
        return true
    }

    override fun setOutOfRange(outOfRange: Boolean): Boolean {
        this.outOfRange = outOfRange
        if (outOfRange) breakLink()
        return true
    }

    override fun loseCommand(command: EmulatedCommand): Boolean = emulator.loseCommand(command)
    override fun loseAnswer(command: EmulatedCommand): Boolean = emulator.loseAnswer(command)

    /** The pump sends the alarm report now, or when the next link is up. False for an alarm it does not send. */
    override fun raise(alarm: EmulatedAlarm): Boolean {
        val report = emulator.alarm(alarm) ?: return false
        synchronized(sendLock) {
            if (connected && notifying) enqueue(report, linkGeneration, System.currentTimeMillis())
            else heldAlarms += report
        }
        return true
    }

    override fun stopNextBolusAt(units: Double): Boolean = emulator.stopNextBolusAt(units)
    override fun shiftPumpClock(offsetMs: Long): Boolean = emulator.shiftClock(offsetMs)
    override fun bolusOnPump(units: Double): Boolean = emulator.bolusOnPump(units)
    override fun suspendOnPump(suspended: Boolean): Boolean = emulator.suspendOnPump(suspended)
    override fun state(): EmulatedPumpState = emulator.snapshot(linkUp = connected)
    override fun saveState(): ByteArray = emulator.saveState()

    // --- BleTransport ---

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

    private val deviceName: String get() = "$NAME_PREFIX${pumpState.serialNumber}"

    /** The pump is always bonded. */
    private inner class EmulatorAdapter : BleAdapter {

        override fun enable() {}
        override fun getDeviceName(address: String): String = deviceName
        override fun isDeviceBonded(address: String): Boolean = true
        override fun createBond(address: String): Boolean = true
        override fun removeBond(address: String) {}
    }

    /**
     * As a real scanner: a listener sees only what the pump advertises after it started to listen. The pump advertises
     * again and again while a scan runs. UNVERIFIED: how often a real G8 advertises.
     */
    private inner class EmulatorScanner : BleScanner {

        private val _scannedDevices = MutableSharedFlow<ScannedDevice>(extraBufferCapacity = 10)
        override val scannedDevices: SharedFlow<ScannedDevice> = _scannedDevices

        @Volatile private var advertising: ScheduledFuture<*>? = null

        @Synchronized
        override fun startScan() {
            advertising?.cancel(false)
            val real = speed == EmulatorSpeed.REAL
            advertising = DiaconnPumpEmulator.timer.scheduleWithFixedDelay(
                { _scannedDevices.tryEmit(ScannedDevice(name = deviceName, address = ADDRESS)) },
                if (real) REAL_FIRST_ADVERTISEMENT_MS else FAST_ADVERTISEMENT_MS,
                if (real) REAL_ADVERTISEMENT_MS else FAST_ADVERTISEMENT_MS,
                TimeUnit.MILLISECONDS
            )
        }

        @Synchronized
        override fun stopScan() {
            advertising?.cancel(false)
            advertising = null
        }
    }

    private inner class EmulatorGatt : BleGatt {

        /**
         * The link comes up on another thread, a moment after this returned, as on a real radio. The driver marks itself
         * "connecting, not connected" after this call; a link that came up inside the call was then never seen as up.
         * Out of range: the connect starts and never comes up.
         */
        override fun connect(address: String): Boolean {
            log("connect($address)")
            if (outOfRange) return true
            val generation = synchronized(sendLock) {
                linkGeneration++
                connected = true
                notifying = false
                linkGeneration
            }
            emulator.newLink()
            launch {
                @Suppress("SleepInsteadOfDelay")
                Thread.sleep(if (speed == EmulatorSpeed.REAL) REAL_CONNECT_MS else FAST_CONNECT_MS)
                if (generation == linkGeneration) listener?.onConnectionStateChanged(true)
            }
            return true
        }

        /**
         * No callback, as in the Equil and Dana RS emulators. A real radio calls back; here a callback that came after the
         * driver connected again would close the new link.
         */
        override fun disconnect() {
            dropLink()
        }

        override fun close() {
            dropLink()
        }

        override fun discoverServices() {
            if (connected) listener?.onServicesDiscovered(true)
        }

        override fun findCharacteristics(): Boolean = connected

        override fun enableNotifications() {
            if (!connected) return
            notifying = true
            listener?.onDescriptorWritten()
            synchronized(sendLock) {
                heldAlarms.forEach { enqueue(it, linkGeneration, System.currentTimeMillis()) }
                heldAlarms.clear()
            }
        }

        /**
         * The pump handles the request at once; its answer goes out after the time the pump takes. With no link the
         * request is lost.
         */
        override fun writeCharacteristic(data: ByteArray) {
            val receivedAt = System.currentTimeMillis()
            synchronized(sendLock) {
                if (!connected) {
                    log("no link, the request is lost")
                    return
                }
                val generation = linkGeneration
                // The driver writes without response; a real radio still reports the write as done.
                sender.execute { if (generation == linkGeneration) listener?.onCharacteristicWritten() }
                val exchange = emulator.receive(data)
                // A request that waited for the pump to carry out a temp basal goes out from then on.
                val handledAt = maxOf(receivedAt, exchange.heldUntil)
                exchange.reportsBefore.forEach { enqueue(it, generation, handledAt) }
                exchange.answer?.let { enqueue(it, generation, handledAt + answerDelayMs()) }
                exchange.reportsAfter.forEach { enqueue(it, generation, handledAt) }
            }
        }
    }

    /** Real G8: answers in 0.1 to 0.4 s. */
    private fun answerDelayMs(): Long =
        if (speed == EmulatorSpeed.REAL) REAL_ANSWER_MIN_MS + (Math.random() * (REAL_ANSWER_MAX_MS - REAL_ANSWER_MIN_MS)).toLong() else FAST_ANSWER_MS

    /** A report the pump sends by itself goes out only on a link that is up. UNVERIFIED: a real pump keeps none for later. */
    private fun sendReport(report: ByteArray) {
        synchronized(sendLock) {
            if (connected && notifying) enqueue(report, linkGeneration, System.currentTimeMillis())
            else log("no link, a report is not sent")
        }
    }

    /** Under [sendLock]. The packet goes out after the ones queued before it, not before [notBefore]. */
    private fun enqueue(packet: ByteArray, generation: Int, notBefore: Long) {
        sender.execute {
            val wait = notBefore - System.currentTimeMillis()
            @Suppress("SleepInsteadOfDelay")
            if (wait > 0) Thread.sleep(wait)
            if (generation == linkGeneration && notifying) {
                try {
                    listener?.onCharacteristicChanged(packet)
                } catch (e: Exception) {
                    aapsLogger?.error(LTag.PUMPEMULATOR, "Diaconn emulator: the driver failed on a packet", e)
                }
            } else log("a packet of an old link is dropped")
        }
    }

    /** @return true when a link was up */
    private fun dropLink(): Boolean = synchronized(sendLock) {
        val wasConnected = connected
        linkGeneration++
        connected = false
        notifying = false
        wasConnected
    }

    private fun launch(block: () -> Unit) {
        val thread = Thread(block, "DiaconnEmulatorLink").apply { isDaemon = true }
        synchronized(pendingThreads) {
            pendingThreads.removeAll { !it.isAlive }
            pendingThreads.add(thread)
        }
        thread.start()
    }

    /** For tests: waits until the link threads and the packets queued so far are done. */
    fun awaitPendingCallbacks(timeoutMs: Long = 5000) {
        synchronized(pendingThreads) { pendingThreads.toList() }.forEach { it.join(timeoutMs) }
        sender.submit {}.get(timeoutMs, TimeUnit.MILLISECONDS)
    }

    private fun log(message: String) = aapsLogger?.debug(LTag.PUMPEMULATOR, "Diaconn emulator link: $message")

    private companion object {

        const val NAME_PREFIX = "DIACONN-"
        const val ADDRESS = "00:00:00:00:00:00"

        const val REAL_ANSWER_MIN_MS = 100L
        const val REAL_ANSWER_MAX_MS = 400L

        /**
         * Short, but longer than nothing: the driver puts a request in its queue of sent requests only after the write
         * returned, and an answer before that is dropped.
         */
        const val FAST_ANSWER_MS = 10L

        /** UNVERIFIED: how long a real G8 takes to come up. */
        const val REAL_CONNECT_MS = 300L
        const val FAST_CONNECT_MS = 10L

        const val REAL_FIRST_ADVERTISEMENT_MS = 200L
        const val REAL_ADVERTISEMENT_MS = 500L
        const val FAST_ADVERTISEMENT_MS = 20L
    }
}
