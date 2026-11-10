package app.aaps.pump.equil.emulator

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
import app.aaps.core.interfaces.pump.emulator.EmulatedPumpState
import app.aaps.core.interfaces.pump.emulator.EmulatorSpeed
import app.aaps.core.interfaces.pump.emulator.PumpEmulatorControl
import app.aaps.pump.equil.ble.EquilBleTransport
import app.aaps.pump.equil.manager.EquilCmdModel
import app.aaps.pump.equil.manager.EquilPacketCodec
import app.aaps.pump.equil.manager.EquilResponse
import app.aaps.pump.equil.manager.Utils
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * BleTransport implementation that emulates an Equil pump.
 *
 * Instead of talking to real Bluetooth hardware, routes data through
 * an [EquilPumpEmulator] for the encrypted command protocol.
 *
 * The flow for each write:
 * 1. App writes 16-byte BLE packet → [BleGatt.writeCharacteristic]
 * 2. Transport reassembles packets until end-bit is set
 * 3. Parses [EquilCmdModel] from reassembled data
 * 4. [EquilPumpEmulator] processes command → generates response model
 * 5. Transport frames response into 16-byte packets
 * 6. Delivers via [BleTransportListener.onCharacteristicChanged]
 *
 * Threading: All operations are synchronous for deterministic testing.
 */
class EquilEmulatorBleTransport(
    val emulator: EquilPumpEmulator = EquilPumpEmulator(),
    private val aapsLogger: AAPSLogger? = null,
    /** Called during startScan to generate/retrieve the serial number. If null, uses current state value. */
    private val serialNumberProvider: (() -> String)? = null,
    /** Called on connect to restore device password from stored preferences. Returns hex string or null. */
    private val storedPasswordProvider: (() -> String?)? = null
) : EquilBleTransport, PumpEmulatorControl {

    private var listener: BleTransportListener? = null
    @Volatile private var connected = false

    /** While true, a connect never comes up. */
    @Volatile private var outOfRange = false

    // --- PumpEmulatorControl ---

    /**
     * Not used by this emulator: a bolus takes the time its command asks for, as on the pump. A test makes boluses
     * shorter with the bolus speed setting of the driver, as a user does.
     */
    @Volatile override var speed: EmulatorSpeed = EmulatorSpeed.REAL

    override fun breakLink(): Boolean {
        // A response still on its way belongs to the old link.
        connectionGeneration++
        connected = false
        listener?.onConnectionStateChanged(false)
        return true
    }

    override fun setOutOfRange(outOfRange: Boolean): Boolean {
        this.outOfRange = outOfRange
        if (outOfRange && connected) breakLink()
        return true
    }

    override fun loseCommand(command: EmulatedCommand): Boolean = emulator.loseCommand(command)
    override fun loseAnswer(command: EmulatedCommand): Boolean = emulator.loseAnswer(command)

    /** The pump logs the alarm: the driver reads it from the pump log at its next connection. */
    override fun raise(alarm: EmulatedAlarm): Boolean = emulator.raise(alarm)

    /**
     * Not copied. How a real Equil goes on after a blockage during a bolus was never seen: whether the bolus stops there,
     * and which log entries follow. A blockage cannot be made on a real pump to look. A guess here would make a test
     * report a driver bug that may not exist.
     */
    override fun stopNextBolusAt(units: Double): Boolean = false

    override fun state(): EmulatedPumpState = emulator.snapshot(linkUp = connected)
    override fun saveState(): ByteArray = emulator.saveState()
    override fun shiftPumpClock(offsetMs: Long): Boolean = emulator.shiftClock(offsetMs)

    @Volatile private var idleClose: ScheduledFuture<*>? = null

    /**
     * Seen on a real pump (#3467, 2026-11-08): it closes the link about 9.5 s after the last message in either direction.
     * During a bolus the driver sends nothing, so after a bolus at 1.5 U/min the link is gone. After a 5 s bolus the
     * driver read the pump log at once, and the link stayed. A time of the pump, so the same at both speeds.
     * Called on every message; the newest call wins.
     */
    @Synchronized
    private fun armIdleClose() {
        val generation = connectionGeneration
        idleClose?.cancel(false)
        idleClose = timer.schedule({
            if (generation == connectionGeneration && connected) {
                aapsLogger?.debug(LTag.PUMPEMULATOR, "EmulatorGatt: the pump closes the idle link")
                breakLink()
            }
        }, LINK_IDLE_CLOSE_MS, TimeUnit.MILLISECONDS)
    }

    // Packet reassembly buffer
    private var receiveBuffer = EquilResponse(System.currentTimeMillis())
    private var pumpReqIndex = 0 // pump-side response index
    private var lastResponseIndex = 0
    @Volatile private var connectionGeneration = 0 // incremented on connect() to detect stale responses after reconnect

    // Track background threads so tests can wait for them to finish
    private val pendingThreads = mutableListOf<Thread>()

    /**
     * Wait for all pending async callbacks to complete.
     * Call this in tests before teardown to avoid Mockito mock-cleared races.
     */
    fun awaitPendingCallbacks(timeoutMs: Long = 2000) {
        val threads = synchronized(pendingThreads) { pendingThreads.toList() }
        for (t in threads) t.join(timeoutMs)
        synchronized(pendingThreads) { pendingThreads.removeAll { !it.isAlive } }
    }

    private fun launchAsync(block: () -> Unit) {
        val t = Thread(block)
        synchronized(pendingThreads) {
            pendingThreads.removeAll { !it.isAlive }
            pendingThreads.add(t)
        }
        t.start()
    }

    val pumpState: EquilPumpState get() = emulator.state

    override var scanAddress: String? = null
    override var onGattError133: (() -> Unit)? = null
    override var onDescriptorWriteFailed: (() -> Unit)? = null

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

    // --- BleAdapter ---

    private inner class EmulatorAdapter : BleAdapter {

        override fun enable() {}
        override fun getDeviceName(address: String): String = pumpState.serialNumber
        override fun isDeviceBonded(address: String): Boolean = true
        override fun createBond(address: String): Boolean = true
        override fun removeBond(address: String) {}
    }

    // --- BleScanner ---

    /**
     * As the real scanner: a listener sees only what the pump advertises after it started to listen, never an older
     * advertisement. The pump advertises all the time, so while a scan runs it is found again and again, each time with
     * what it holds now. An older advertisement kept for new listeners had the serial of before a discovery scan gave the
     * pump a new one, so the wizard paired with a serial the pump no longer had, and with an old log index.
     */
    private inner class EmulatorScanner : BleScanner {

        private val _scannedDevices = MutableSharedFlow<ScannedDevice>(extraBufferCapacity = 10)
        override val scannedDevices: SharedFlow<ScannedDevice> = _scannedDevices

        @Volatile private var advertising: ScheduledFuture<*>? = null

        @Synchronized
        override fun startScan() {
            // Generate new serial only for discovery scans (pairing), not reconnections
            if (scanAddress.isNullOrEmpty()) {
                serialNumberProvider?.invoke()?.let { pumpState.serialNumber = it }
            }
            advertising?.cancel(false)
            // Seen on a real pump (2026-11-08): found 0.2 to 1 s after the scan started. UNVERIFIED: how often it advertises.
            val real = speed == EmulatorSpeed.REAL
            advertising = timer.scheduleWithFixedDelay(
                {
                    _scannedDevices.tryEmit(
                        ScannedDevice(
                            name = "Equil - ${pumpState.serialNumber}",
                            address = "00:00:00:00:00:00",
                            scanRecordBytes = buildAdvertisementData()
                        )
                    )
                },
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

    // --- BleGatt ---

    private inner class EmulatorGatt : BleGatt {

        override fun connect(address: String): Boolean {
            aapsLogger?.debug(LTag.PUMPEMULATOR, "EmulatorGatt: connect($address)")
            // Out of range: the connect is started and never comes up, as with a real pump.
            if (outOfRange) return true
            receiveBuffer = EquilResponse(System.currentTimeMillis())
            pumpReqIndex = 0
            connectionGeneration++
            emulator.reset()
            // Restore device password from stored preferences (survives app restart)
            storedPasswordProvider?.invoke()?.let { pwd ->
                if (pwd.isNotEmpty()) {
                    emulator.state.devicePassword = Utils.hexStringToBytes(pwd)
                    aapsLogger?.debug(LTag.PUMPEMULATOR, "EmulatorGatt: restored device password from preferences")
                }
            }
            connected = true
            armIdleClose()
            listener?.onConnectionStateChanged(true)
            return true
        }

        override fun disconnect() {
            connected = false
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

        /**
         * All buffer mutations are synchronized to prevent concurrent corruption.
         *
         * The callback is fired asynchronously AFTER the synchronized block returns,
         * ensuring indexData++ in EquilBLE.writeData() completes before the next write.
         * The DanaRS emulator uses the same pattern (Thread + sleep after write).
         */
        @Suppress("SleepInsteadOfDelay")
        override fun writeCharacteristic(data: ByteArray) {
            val gen = connectionGeneration
            armIdleClose()

            // Captured atomically inside the synchronized block when end-bit is set.
            // This prevents the callback thread from resetting receiveBuffer
            // while we're reading it for message processing.
            var capturedMessage: EquilResponse? = null
            var isEndPacket = false
            var isShortMessage = false
            var isRawCommand = false

            synchronized(this) {
                aapsLogger?.debug(LTag.PUMPEMULATOR, "EmulatorGatt: writeCharacteristic ${data.size} bytes (gen=$gen)")

                // Reset buffer when a new message starts (offset 0 with stale data from previous command).
                // Must happen BEFORE validatePacket, otherwise offset=0 from a new message
                // is rejected as a "duplicate" of the previous message's offset=0 packet.
                val packetOffset = data[3].toInt() and 0xFF
                if (packetOffset == 0 && receiveBuffer.send.isNotEmpty()) {
                    aapsLogger?.debug(LTag.PUMPEMULATOR, "EmulatorGatt: new message detected, resetting buffer")
                    receiveBuffer = EquilResponse(System.currentTimeMillis())
                }

                // Validate CRC8 and duplicate offset
                if (!EquilPacketCodec.validatePacket(data, receiveBuffer)) {
                    aapsLogger?.debug(LTag.PUMPEMULATOR, "EmulatorGatt: invalid packet (CRC or duplicate)")
                    return
                }

                // Add to reassembly buffer
                receiveBuffer.add(ByteBuffer.wrap(data.copyOf()))

                // Check end bit and atomically capture the buffer if message is complete
                if (EquilPacketCodec.isEnd(data[4])) {
                    isEndPacket = true
                    if (receiveBuffer.send.isNotEmpty()) {
                        // Count payload bytes
                        var totalPayloadBytes = 0
                        for ((idx, buf) in receiveBuffer.send.withIndex()) {
                            val bs = buf.array()
                            totalPayloadBytes += if (idx == 0) maxOf(0, bs.size - 10) else maxOf(0, bs.size - 6)
                        }
                        if (totalPayloadBytes < 28) {
                            isShortMessage = true
                            isRawCommand = receiveBuffer.send.size == 1 && receiveBuffer.send.first().array().size == 14
                        } else {
                            capturedMessage = receiveBuffer
                        }
                    }
                    receiveBuffer = EquilResponse(System.currentTimeMillis())
                }
            }
            // synchronized block released — indexData++ in writeData() can now execute

            // Fire callback asynchronously with realistic BLE timing.
            // Real Android BLE callbacks arrive on binder threads ~10-30ms after the write.
            // EquilBLE.onCharacteristicWritten() then sleeps 20ms (EQUIL_BLE_WRITE_TIME_OUT)
            // before calling writeData(). Total per-packet: delay + 20ms sleep.
            // Must be async (not synchronous) to avoid infinite recursion / stack overflow.
            launchAsync {
                Thread.sleep(25)
                if (gen == connectionGeneration) listener?.onCharacteristicWritten()
            }

            if (!isEndPacket) return

            // Handle short/raw messages
            if (isShortMessage) {
                if (isRawCommand) {
                    handleRawPacket()
                } else {
                    aapsLogger?.debug(LTag.PUMPEMULATOR, "EmulatorGatt: discarding short message")
                }
                return
            }

            // Process the captured message (safe from concurrent modification)
            val messageBuffer = capturedMessage ?: return
            try {
                val firstPacket = messageBuffer.send.first().array()
                if (firstPacket.size < 10) {
                    aapsLogger?.debug(LTag.PUMPEMULATOR, "EmulatorGatt: discarding short first packet (${firstPacket.size} bytes)")
                    return
                }
                val incomingPort = Utils.bytesToHex(byteArrayOf(firstPacket[6], firstPacket[7], firstPacket[8], firstPacket[9]))

                val incomingModel = EquilPacketCodec.parseModel(messageBuffer)

                val isPairing = incomingPort.startsWith("0D0D")
                val isInitialRequest = incomingPort.startsWith("0F0F")

                // When a new initial request arrives (port 0F0F) but the emulator
                // is mid-session (AWAITING_COMMAND/CONFIRM), reset to AWAITING_INITIAL.
                // This happens when the app starts a new command via ready() while
                // the emulator still has an active session from the previous command.
                if (isInitialRequest) {
                    emulator.resetToInitial()
                }

                val responseModel: EquilCmdModel?
                val responsePort: String

                if (isPairing) {
                    val convertedSn = pumpState.serialNumber.toCharArray().joinToString("") { "0$it" }
                    responseModel = emulator.processPairInitial(incomingModel, convertedSn)
                    responsePort = emulator.getPairResponsePort()
                } else {
                    val password = emulator.getCurrentPassword()
                    responseModel = emulator.processMessage(incomingModel, password, incomingPort)
                    responsePort = emulator.getResponsePort()
                }

                if (responseModel == null) return

                responseModel.code = responsePort.substring(4)
                // The ack of a confirmation repeats the index of the answer it confirms, see lastResponseWasConfirmationAck.
                val index = if (!isPairing && emulator.lastResponseWasConfirmationAck) lastResponseIndex else pumpReqIndex++
                lastResponseIndex = index
                val responsePackets = EquilPacketCodec.buildPackets(responseModel, responsePort, index, System.currentTimeMillis())

                deliverResponse(gen, responsePackets)
            } catch (e: Exception) {
                aapsLogger?.error(LTag.PUMPEMULATOR, "EmulatorGatt: error processing message", e)
            }
        }

        override fun requestConnectionPriority(priority: Int) {} // no-op
    }

    /**
     * Handle raw (unencrypted) CmdDevicesOldGet protocol.
     *
     * 3 phases:
     * - Phase 0: Initial 14-byte request → respond with firmware version
     * - Phase 1: App sends getFirstData (0x02,0x00) → respond with device info
     * - Phase 2: App sends getNextData confirmation → respond with firmware + done
     *
     * CmdDevicesOldGet overrides decodeModel() to use a raw format (tag="", iv="",
     * ciphertext = raw bytes). The emulator responds in the same raw format.
     */
    /**
     * Deliver response packets asynchronously on a separate thread.
     * Checks generation to prevent stale responses after reconnect.
     */
    @Suppress("SleepInsteadOfDelay")
    private fun deliverResponse(gen: Int, responsePackets: EquilResponse) {
        launchAsync {
            // Seen on a real pump (2026-11-08): the first packet of an answer comes 60 to 100 ms after the last packet of
            // the command, the next ones 10 to 65 ms apart. The real-speed run copies that, to find races that only show
            // when an answer does not come at once.
            val real = speed == EmulatorSpeed.REAL
            Thread.sleep(if (real) REAL_ANSWER_MS else FAST_ANSWER_MS)
            if (gen == connectionGeneration) {
                for ((i, buf) in responsePackets.send.withIndex()) {
                    if (i > 0 && real) Thread.sleep(REAL_PACKET_GAP_MS)
                    // A link that broke during the answer gets no more of it.
                    if (gen != connectionGeneration) break
                    listener?.onCharacteristicChanged(buf.array())
                }
                armIdleClose()
            } else {
                aapsLogger?.debug(LTag.PUMPEMULATOR, "EmulatorGatt: discarding stale response (gen=$gen, current=$connectionGeneration)")
            }
        }
    }

    /**
     * Handle raw (unencrypted) messages from CmdDevicesOldGet.
     * Responds with firmware version data in the raw format (tag="", iv="").
     */
    private fun handleRawPacket() {
        aapsLogger?.debug(LTag.PUMPEMULATOR, "EmulatorGatt: handling raw packet")

        val fwMajor = pumpState.firmwareVersion.toInt()
        val fwMinor = ((pumpState.firmwareVersion - fwMajor) * 10).toInt()

        // CmdDevicesOldGet.decodeModel() extracts ciphertext with 2-byte offset
        // decode() reads ciphertext[12],[13] → responseData[14],[15]
        // decodeConfirmData() reads data[18],[19] → responseData[20],[21]
        val responseData = ByteArray(22)
        responseData[14] = fwMajor.toByte()
        responseData[15] = fwMinor.toByte()
        responseData[20] = fwMajor.toByte()
        responseData[21] = fwMinor.toByte()

        val model = EquilCmdModel()
        model.tag = ""
        model.iv = ""
        model.ciphertext = Utils.bytesToHex(responseData)
        model.code = "0E0E"

        val responsePackets = EquilPacketCodec.buildPackets(model, "0000" + model.code, pumpReqIndex, System.currentTimeMillis())
        pumpReqIndex++

        deliverResponse(connectionGeneration, responsePackets)
    }

    /**
     * The advertisement, as EquilManager.decodeData() reads it. The newest log index is how the driver learns that there
     * is something to read: with 0 here it never read the pump log of the emulator.
     */
    private fun buildAdvertisementData(): ByteArray {
        // [17] battery, [18] insulin, [19-20] basal rate, [23-24] newest log index (low byte first),
        // [25-27] port, type and level of the newest log entry.
        val (newest, basalSpeed) = emulator.advertised()
        val data = ByteArray(62)
        data[17] = pumpState.battery.toByte()
        data[18] = pumpState.currentInsulin.toByte()
        data[19] = (basalSpeed and 0xFF).toByte()
        data[20] = ((basalSpeed shr 8) and 0xFF).toByte()
        val index = newest?.index ?: 0
        data[23] = (index and 0xFF).toByte()
        data[24] = ((index shr 8) and 0xFF).toByte()
        if (newest != null) {
            data[25] = newest.port.toByte()
            data[26] = newest.type.toByte()
            data[27] = newest.level.toByte()
        }
        return data
    }

    private companion object {

        /** See armIdleClose: 9.5 to 9.9 s on a real pump. */
        const val LINK_IDLE_CLOSE_MS = 9_500L

        /** See deliverResponse. */
        const val REAL_ANSWER_MS = 100L
        const val REAL_PACKET_GAP_MS = 40L
        const val FAST_ANSWER_MS = 5L

        /** See EmulatorScanner. */
        const val REAL_FIRST_ADVERTISEMENT_MS = 200L
        const val REAL_ADVERTISEMENT_MS = 500L
        const val FAST_ADVERTISEMENT_MS = 20L

        /** The pump's own times: the idle close and the advertisements. One daemon thread for all, so a test never waits for it. */
        val timer: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "EquilEmulatorPumpTimer").apply { isDaemon = true }
        }
    }
}
