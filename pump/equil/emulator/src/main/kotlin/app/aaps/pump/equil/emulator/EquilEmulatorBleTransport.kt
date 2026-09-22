package app.aaps.pump.equil.emulator

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.ble.BleAdapter
import app.aaps.core.interfaces.pump.ble.BleGatt
import app.aaps.core.interfaces.pump.ble.BleScanner
import app.aaps.core.interfaces.pump.ble.BleTransportListener
import app.aaps.core.interfaces.pump.ble.PairingState
import app.aaps.core.interfaces.pump.ble.ScannedDevice
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
) : EquilBleTransport {

    private var listener: BleTransportListener? = null
    private var connected = false

    // Packet reassembly buffer
    private var receiveBuffer = EquilResponse(System.currentTimeMillis())
    private var pumpReqIndex = 0 // pump-side response index
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

    private inner class EmulatorScanner : BleScanner {

        private val _scannedDevices = MutableSharedFlow<ScannedDevice>(replay = 1, extraBufferCapacity = 10)
        override val scannedDevices: SharedFlow<ScannedDevice> = _scannedDevices

        override fun startScan() {
            // Generate new serial only for discovery scans (pairing), not reconnections
            if (scanAddress.isNullOrEmpty()) {
                serialNumberProvider?.invoke()?.let { pumpState.serialNumber = it }
            }
            // Immediately emit a fake device with scan record containing pump advertisement data
            _scannedDevices.tryEmit(
                ScannedDevice(
                    name = "Equil - ${pumpState.serialNumber}",
                    address = "00:00:00:00:00:00",
                    scanRecordBytes = buildAdvertisementData()
                )
            )
        }

        override fun stopScan() {}
    }

    // --- BleGatt ---

    private inner class EmulatorGatt : BleGatt {

        override fun connect(address: String): Boolean {
            aapsLogger?.debug(LTag.PUMPEMULATOR, "EmulatorGatt: connect($address)")
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
                    responseModel = emulator.processMessage(incomingModel, password)
                    responsePort = emulator.getResponsePort()
                }

                if (responseModel == null) return

                responseModel.code = responsePort.substring(4)
                val responsePackets = EquilPacketCodec.buildPackets(responseModel, responsePort, pumpReqIndex, System.currentTimeMillis())
                pumpReqIndex++

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
            Thread.sleep(5) // Simulate BLE response latency
            if (gen == connectionGeneration) {
                for (buf in responsePackets.send) {
                    listener?.onCharacteristicChanged(buf.array())
                }
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
     * Build fake advertisement data.
     * The scan record format is pump-specific — for now return minimal valid data
     * that won't crash EquilManager.decodeData().
     */
    private fun buildAdvertisementData(): ByteArray {
        // Scan record layout read by EquilManager.decodeData():
        // [17] = battery, [18] = insulin
        val data = ByteArray(62)
        data[17] = pumpState.battery.toByte()
        data[18] = pumpState.currentInsulin.toByte()
        return data
    }
}
