package app.aaps.pump.danars.emulator

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.danars.encryption.BleEncryption
import app.aaps.pump.danars.encryption.EncryptionType
import app.aaps.core.interfaces.pump.ble.BleAdapter
import app.aaps.core.interfaces.pump.ble.BleGatt
import app.aaps.core.interfaces.pump.ble.BleScanner
import app.aaps.core.interfaces.pump.ble.BleTransport
import app.aaps.core.interfaces.pump.ble.BleTransportListener
import app.aaps.core.interfaces.pump.ble.PairingState
import app.aaps.core.interfaces.pump.ble.ScannedDevice
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * BleTransport implementation that emulates a Dana RS pump.
 *
 * Instead of talking to a real Bluetooth device, this routes data through
 * a [PumpEmulator] with a pump-side [BleEncryption] instance for the
 * encryption handshake.
 *
 * The flow for each write:
 * 1. App writes encrypted bytes → [BleGatt.writeCharacteristic]
 * 2. Pump-side decrypts → extracts opcode + params
 * 3. [PumpEmulator] processes command → generates response data
 * 4. Pump-side encrypts response → calls [BleTransportListener.onCharacteristicChanged]
 * 5. BLEComm decrypts and routes to packet handler
 *
 * Threading: All operations are synchronous (no BLE delays).
 * This makes tests fast and deterministic.
 *
 * @param encryptionType The encryption variant to emulate:
 *   - [EncryptionType.ENCRYPTION_DEFAULT]: v1 handshake (PUMP_CHECK → CHECK_PASSKEY → TIME_INFO)
 *   - [EncryptionType.ENCRYPTION_RSv3]: v3 handshake (PUMP_CHECK 9-byte → TIME_INFO with pairing keys)
 *   - [EncryptionType.ENCRYPTION_BLE5]: BLE5 handshake (PUMP_CHECK 14-byte → TIME_INFO)
 */
class EmulatorBleTransport(
    val emulator: PumpEmulator = PumpEmulator(),
    deviceName: String = "UHH00002TI",
    private val encryptionType: EncryptionType = EncryptionType.ENCRYPTION_DEFAULT,
    private val pumpDisplay: PumpDisplay = NoOpPumpDisplay,
    private val aapsLogger: AAPSLogger? = null,
    /** Called during startScan to generate a new device name. If null, uses current name. */
    private val deviceNameProvider: (() -> String)? = null
) : BleTransport {

    /** Current emulated device name. Changes when [deviceNameProvider] returns a new value on scan. */
    var currentDeviceName: String = deviceName
        private set

    /** Delay before onCharacteristicWritten callback (simulates BLE write latency). Tests can set to 0. */
    var writeLatencyMs: Long = 1L
    /** Delay before sending deferred PASSKEY_RETURN during v1 pairing. Tests can set to 0. */
    var pairingDelayMs: Long = 100L

    /** Pump-side encryption instance (mirrors the app-side BleEncryption) */
    val pumpEncryption = BleEncryption()

    private fun syncDeviceName(name: String) {
        currentDeviceName = name
        for (i in name.indices) {
            if (i < 10) pumpEncryption.deviceName[i] = name[i].code.toUByte()
        }
        pumpState.serialNumber = name
    }

    init {
        syncDeviceName(deviceName)
    }

    private var listener: BleTransportListener? = null
    private var connected = false

    // Read buffer for reassembling chunked packets
    private val readBuffer = ByteArray(1024)
    private var bufferLength = 0
    @Volatile private var connectionGeneration = 0
    private var v3PairingRequested = false // true after TIME_INFO with requestNewPairing=1

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

    val pumpState: PumpState get() = emulator.state

    init {
        // Set hwModel to match encryption type so BLEComm's handshake takes the right path
        when (encryptionType) {
            EncryptionType.ENCRYPTION_DEFAULT -> pumpState.hwModel = 0x05 // Dana RS
            EncryptionType.ENCRYPTION_RSv3    -> pumpState.hwModel = 0x05 // Dana RS (RSv3)
            EncryptionType.ENCRYPTION_BLE5    -> pumpState.hwModel = 0x09 // Dana-i (BLE5)
        }
        // productCode >= 2 required to avoid UNSUPPORTED_FIRMWARE notification
        pumpState.productCode = 2
        // Serial number should match device name (in real Dana RS they're the same)
        pumpState.serialNumber = currentDeviceName

        // Wire up spontaneous message callback for notifications and multi-response history
        emulator.onSpontaneousMessage = { type, opCode, data ->
            val packet = buildRawPacket(type, opCode, data, applyFullEncoding = pumpEncryption.connectionState == 2)
            sendResponse(packet)
        }
    }

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

        override fun enable() {} // no-op
        override fun getDeviceName(address: String): String = currentDeviceName
        override fun isDeviceBonded(address: String): Boolean = true
        override fun createBond(address: String): Boolean = true
        override fun removeBond(address: String) {}
    }

    // --- BleScanner ---

    private inner class EmulatorScanner : BleScanner {

        private val _scannedDevices = MutableSharedFlow<ScannedDevice>(replay = 1, extraBufferCapacity = 10)
        override val scannedDevices: SharedFlow<ScannedDevice> = _scannedDevices

        override fun startScan() {
            // Generate new device name if provider is set
            deviceNameProvider?.invoke()?.let { syncDeviceName(it) }
            // Immediately report the emulated device
            aapsLogger?.debug(LTag.PUMPEMULATOR, "EmulatorScanner: startScan emitting device=$currentDeviceName")
            val emitted = _scannedDevices.tryEmit(ScannedDevice(name = currentDeviceName, address = "00:00:00:00:00:00"))
            aapsLogger?.debug(LTag.PUMPEMULATOR, "EmulatorScanner: tryEmit result=$emitted subscribers=${_scannedDevices.subscriptionCount.value}")
        }

        override fun stopScan() {} // no-op
    }

    // --- BleGatt ---

    private inner class EmulatorGatt : BleGatt {

        override fun connect(address: String): Boolean {
            aapsLogger?.debug(LTag.PUMPEMULATOR, "connect($address) encryptionType=$encryptionType")
            // Reset encryption state for new connection (connectionState=2 from previous session
            // would cause handshake packets to be treated as second-level encrypted)
            pumpEncryption.connectionState = 0
            pumpEncryption.setEnhancedEncryption(EncryptionType.ENCRYPTION_DEFAULT)
            bufferLength = 0
            connectionGeneration++
            v3PairingRequested = false
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

        override fun writeCharacteristic(data: ByteArray) {
            // Capture current generation to detect stale writes from previous connections
            val gen = connectionGeneration
            aapsLogger?.debug(LTag.PUMPEMULATOR, "writeCharacteristic ${data.size} bytes (gen=$gen): ${data.joinToString(" ") { "%02X".format(it) }}")
            // Accumulate chunks into buffer first, THEN notify write completed.
            // In real BLE, onCharacteristicWritten fires asynchronously after write.
            // Firing it before addToBuffer would cause BLEComm to send the next chunk
            // before the remaining chunks are queued (sendMessage splits after first write).
            if (gen != connectionGeneration) {
                aapsLogger?.debug(LTag.PUMPEMULATOR, "ignoring stale write (gen=$gen, current=$connectionGeneration)")
                return
            }
            addToBuffer(data)

            // Notify write completed asynchronously — triggers BLEComm to send the next chunk.
            // In real BLE, this callback fires on the GATT callback thread after the write
            // completes, so there's always latency. We must mimic this because BLEComm adds
            // remaining chunks to mSendQueue AFTER writeCharacteristic returns — a synchronous
            // callback would find an empty queue.
            launchAsync {
                @Suppress("SleepInsteadOfDelay")
                if (writeLatencyMs > 0) Thread.sleep(writeLatencyMs) // Simulate BLE write latency — ensures sendMessage finishes queuing
                listener?.onCharacteristicWritten()
            }

            // Try to parse a complete packet
            val packet = extractPacket()
            if (packet == null) {
                aapsLogger?.debug(LTag.PUMPEMULATOR, "extractPacket: incomplete, bufferLength=$bufferLength")
                return
            }

            // Process the packet through pump-side encryption and emulator
            try {
                val response = processPumpSide(packet)
                if (response != null) {
                    aapsLogger?.debug(LTag.PUMPEMULATOR, "sending response ${response.size} bytes")
                    sendResponse(response)
                } else {
                    aapsLogger?.warn(LTag.PUMPEMULATOR, "processPumpSide returned null (CRC mismatch?)")
                }
            } catch (e: Exception) {
                aapsLogger?.error(LTag.PUMPEMULATOR, "exception in processPumpSide", e)
            }
        }
    }

    // --- Packet processing ---

    private fun addToBuffer(data: ByteArray) {
        synchronized(readBuffer) {
            System.arraycopy(data, 0, readBuffer, bufferLength, data.size)
            bufferLength += data.size
        }
    }

    /**
     * Extract a complete packet from the buffer.
     *
     * For RSv3/BLE5 at connectionState=2, the entire packet is second-level encrypted,
     * including markers. We decrypt a TEMPORARY COPY to find packet boundaries,
     * leaving the original buffer untouched until a complete packet is found.
     * This prevents double-decryption when packets arrive in multiple chunks.
     */
    private fun extractPacket(): ByteArray? {
        synchronized(readBuffer) {
            if (bufferLength < 6) return null

            val isSecondLevelEncrypted = pumpEncryption.connectionState == 2 && encryptionType != EncryptionType.ENCRYPTION_DEFAULT

            // Work on a temporary copy — never modify readBuffer during probing.
            // Save randomSyncKey before trial decryption (RSv3 is stateful per-byte).
            val savedRandomSyncKey = pumpEncryption.randomSyncKey
            val probe: ByteArray = if (isSecondLevelEncrypted) {
                pumpEncryption.decryptSecondLevelPacket(readBuffer.copyOf(bufferLength))
            } else {
                readBuffer.copyOf(bufferLength)
            }

            // Find start markers: [A5 A5] for DEFAULT/RSv3, [AA AA] for BLE5
            val isStandardStart = probe[0] == 0xA5.toByte() && probe[1] == 0xA5.toByte()
            val isBle5Start = probe[0] == 0xAA.toByte() && probe[1] == 0xAA.toByte()

            if (!isStandardStart && !isBle5Start) {
                pumpEncryption.randomSyncKey = savedRandomSyncKey
                bufferLength = 0
                return null
            }

            val endByte = if (isBle5Start) 0xEE.toByte() else 0x5A.toByte()
            val length = probe[2].toInt() and 0xFF
            val totalLength = length + 7

            if (bufferLength < totalLength) {
                // Incomplete — restore randomSyncKey so next attempt re-decrypts from scratch
                pumpEncryption.randomSyncKey = savedRandomSyncKey
                return null // need more chunks
            }

            if (probe[totalLength - 2] != endByte || probe[totalLength - 1] != endByte) {
                pumpEncryption.randomSyncKey = savedRandomSyncKey
                bufferLength = 0
                return null
            }

            // Complete packet found. For RSv3 (stateful randomSyncKey), re-decrypt
            // only the exact packet bytes so the key advances correctly.
            val packet: ByteArray
            if (isSecondLevelEncrypted && encryptionType == EncryptionType.ENCRYPTION_RSv3) {
                pumpEncryption.randomSyncKey = savedRandomSyncKey
                packet = pumpEncryption.decryptSecondLevelPacket(readBuffer.copyOf(totalLength))
            } else {
                packet = probe.copyOf(totalLength)
            }

            // Remove consumed bytes from the original (encrypted) buffer
            val remaining = bufferLength - totalLength
            if (remaining > 0) {
                System.arraycopy(readBuffer, totalLength, readBuffer, 0, remaining)
            }
            bufferLength = remaining

            return packet
        }
    }

    /**
     * Process a complete packet from the app side.
     * For RSv3/BLE5 commands, the second-level encryption is already stripped by extractPacket.
     * Decrypt first-level (SN encoding), extract the command, run through emulator.
     */
    private fun processPumpSide(packet: ByteArray): ByteArray? {
        val decrypted = pumpEncryption.getDecryptedPacket(packet)
        if (decrypted == null) {
            aapsLogger?.warn(LTag.PUMPEMULATOR, "getDecryptedPacket failed (CRC mismatch), packet=${packet.joinToString(" ") { "%02X".format(it) }}")
            return null
        }

        val type = decrypted[0].toInt() and 0xFF
        val opCode = decrypted[1].toInt() and 0xFF
        aapsLogger?.debug(LTag.PUMPEMULATOR, "decrypted type=0x${"%02X".format(type)} opCode=0x${"%02X".format(opCode)}")

        return when (type) {
            BleEncryption.DANAR_PACKET__TYPE_ENCRYPTION_REQUEST.toInt() -> handleEncryptionRequest(opCode, decrypted)
            BleEncryption.DANAR_PACKET__TYPE_COMMAND.toInt()            -> handleCommand(opCode, decrypted)

            else                                                        -> {
                aapsLogger?.warn(LTag.PUMPEMULATOR, "unknown packet type: 0x${"%02X".format(type)}")
                null
            }
        }
    }

    // --- Encryption handshake ---

    private fun handleEncryptionRequest(opCode: Int, decrypted: ByteArray): ByteArray {
        return when (opCode) {
            BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__PUMP_CHECK       -> handlePumpCheck()

            BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__CHECK_PASSKEY    -> {
                if (decrypted.size > 3) {
                    pumpEncryption.passKey[0] = decrypted[2].toUByte()
                    pumpEncryption.passKey[1] = decrypted[3].toUByte()
                    pumpEncryption.cfPassKey[0] = decrypted[2].toUByte()
                    pumpEncryption.cfPassKey[1] = decrypted[3].toUByte()
                }
                buildEncryptionResponse(opCode, byteArrayOf(0x00))
            }

            BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__TIME_INFORMATION -> handleTimeInformation(opCode, decrypted)

            BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__PASSKEY_REQUEST  -> {
                // v1: Pump shows pairing confirmation on its display. Emulator auto-confirms.
                pumpDisplay.showPairingConfirmation()
                // After confirming, the pump spontaneously sends PASSKEY_RETURN with the pairing key.
                // This must be deferred so the OK response is delivered first.
                launchAsync {
                    @Suppress("SleepInsteadOfDelay")
                    if (pairingDelayMs > 0) Thread.sleep(pairingDelayMs)
                    val returnPacket = buildEncryptionResponse(
                        BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__PASSKEY_RETURN,
                        pumpState.pairingKey
                    )
                    sendResponse(returnPacket)
                }
                buildEncryptionResponse(BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__PASSKEY_REQUEST, byteArrayOf(0x00))
            }

            BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__PASSKEY_RETURN   -> {
                buildEncryptionResponse(BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__PASSKEY_RETURN, pumpState.pairingKey)
            }

            else                                                            -> buildEncryptionResponse(opCode, byteArrayOf(0x00))
        }
    }

    private fun handlePumpCheck(): ByteArray {
        val opCode = BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__PUMP_CHECK
        return when (encryptionType) {
            EncryptionType.ENCRYPTION_DEFAULT -> {
                buildEncryptionResponse(opCode, byteArrayOf('O'.code.toByte(), 'K'.code.toByte()))
            }

            EncryptionType.ENCRYPTION_RSv3    -> {
                val data = byteArrayOf(
                    'O'.code.toByte(), 'K'.code.toByte(),
                    0x00, pumpState.hwModel.toByte(),
                    0x00, pumpState.protocol.toByte(),
                    pumpState.v3RandomSyncKey
                )
                pumpEncryption.setEnhancedEncryption(EncryptionType.ENCRYPTION_RSv3)
                buildEncryptionResponse(opCode, data)
            }

            EncryptionType.ENCRYPTION_BLE5    -> {
                val ble5Key = pumpState.ble5PairingKey.encodeToByteArray()
                val data = byteArrayOf(
                    'O'.code.toByte(), 'K'.code.toByte(),
                    0x00, pumpState.hwModel.toByte(),
                    0x00, pumpState.protocol.toByte(),
                ) + ble5Key
                pumpEncryption.setEnhancedEncryption(EncryptionType.ENCRYPTION_BLE5)
                pumpEncryption.setBle5Key(ble5Key)
                buildEncryptionResponse(opCode, data)
            }
        }
    }

    private fun handleTimeInformation(opCode: Int, decrypted: ByteArray): ByteArray {
        return when (encryptionType) {
            EncryptionType.ENCRYPTION_DEFAULT -> {
                val pass = pumpState.pumpPassword.toInt(16) xor 3463
                val passLow = (pass and 0xFF).toByte()
                val passHigh = ((pass shr 8) and 0xFF).toByte()
                val timeBytes = getTimeBytes()
                val responseData = timeBytes + byteArrayOf(passLow, passHigh)

                pumpEncryption.connectionState = 2
                for (i in 0..5) pumpEncryption.timeInfo[i] = timeBytes[i].toUByte()
                pumpEncryption.password[0] = (passLow.toUByte() xor 0x87u).toUByte()
                pumpEncryption.password[1] = (passHigh.toUByte() xor 0x0Du).toUByte()

                buildEncryptionResponse(opCode, responseData)
            }

            EncryptionType.ENCRYPTION_RSv3    -> {
                // Check if app is requesting new pairing (payload byte = 0x01)
                val requestNewPairing = decrypted.size > 2 && decrypted[2] == 0x01.toByte()
                if (requestNewPairing) {
                    // Show PIN codes on "pump display" for user to copy to EnterPinActivity
                    v3PairingRequested = true
                    pumpDisplay.showPairingPinCodes(
                        pin1 = computeV3Pin1(),
                        pin2 = computeV3Pin2()
                    )
                }

                val response = buildEncryptionResponse(opCode, byteArrayOf(0x00))
                if (!requestNewPairing) {
                    // Set up encryption for commands.
                    // After fresh pairing (finishV3Pairing), the app uses randomSyncKey=0
                    // → initialRandomSyncKey(). On reconnection with stored keys, the app
                    // uses the stored randomSyncKey → decryptionRandomSyncKey().
                    val syncKey: Byte = if (v3PairingRequested) 0 else pumpState.v3RandomSyncKey
                    v3PairingRequested = false
                    pumpEncryption.setPairingKeys(pumpState.v3PairingKey, pumpState.v3RandomPairingKey, syncKey)
                    pumpEncryption.connectionState = 2
                }
                // If new pairing, connectionState stays at 0/1 — will advance after finishV3Pairing()
                response
            }

            EncryptionType.ENCRYPTION_BLE5    -> {
                val response = buildEncryptionResponse(opCode, byteArrayOf(0x00))
                pumpEncryption.connectionState = 2
                response
            }
        }
    }

    /**
     * Compute PIN 1 for RSv3 pairing: 12 hex digits = pairingKey (6 bytes).
     * This is what the real pump displays on its screen.
     */
    private fun computeV3Pin1(): String =
        pumpState.v3PairingKey.joinToString("") { "%02X".format(it) }

    /**
     * Compute PIN 2 for RSv3 pairing: 6 hex digits (randomPairingKey) + 2 hex digits (checksum).
     * Checksum = XOR of all pairingKey bytes XOR all randomPairingKey bytes.
     */
    private fun computeV3Pin2(): String {
        var checksum: Byte = 0
        for (b in pumpState.v3PairingKey) checksum = (checksum.toInt() xor b.toInt()).toByte()
        for (b in pumpState.v3RandomPairingKey) checksum = (checksum.toInt() xor b.toInt()).toByte()
        val randomHex = pumpState.v3RandomPairingKey.joinToString("") { "%02X".format(it) }
        return randomHex + "%02X".format(checksum)
    }

    // --- Command handling ---

    private fun handleCommand(opCode: Int, decrypted: ByteArray): ByteArray {
        val params = if (decrypted.size > 2) decrypted.copyOfRange(2, decrypted.size) else ByteArray(0)
        val responseData = emulator.processCommand(opCode, params)
        return buildCommandResponse(opCode, responseData)
    }

    // --- Packet building ---

    private fun buildEncryptionResponse(opCode: Int, data: ByteArray): ByteArray {
        return buildRawPacket(BleEncryption.DANAR_PACKET__TYPE_ENCRYPTION_RESPONSE.toInt(), opCode, data, applyFullEncoding = false)
    }

    private fun buildCommandResponse(opCode: Int, data: ByteArray): ByteArray {
        return buildRawPacket(BleEncryption.DANAR_PACKET__TYPE_RESPONSE, opCode, data, applyFullEncoding = pumpEncryption.connectionState == 2)
    }

    private fun buildRawPacket(type: Int, opCode: Int, data: ByteArray, applyFullEncoding: Boolean): ByteArray {
        val len = 2 + data.size
        val totalSize = 2 + 1 + len + 2 + 2
        val packet = ByteArray(totalSize)

        packet[0] = 0xA5.toByte()
        packet[1] = 0xA5.toByte()
        packet[2] = len.toByte()
        packet[3] = type.toByte()
        packet[4] = opCode.toByte()
        System.arraycopy(data, 0, packet, 5, data.size)

        val crcData = UByteArray(len)
        for (i in 0 until len) crcData[i] = packet[3 + i].toUByte()
        val crc = pumpEncryption.generateCrc(crcData)
        packet[totalSize - 4] = (crc.toInt() shr 8).toByte()
        packet[totalSize - 3] = crc.toByte()
        packet[totalSize - 2] = 0x5A.toByte()
        packet[totalSize - 1] = 0x5A.toByte()

        val uPacket = UByteArray(totalSize) { packet[it].toUByte() }
        pumpEncryption.encodeArrayBySn(uPacket)

        if (applyFullEncoding) {
            when (encryptionType) {
                EncryptionType.ENCRYPTION_DEFAULT -> {
                    pumpEncryption.encodeArrayByTime(uPacket, pumpEncryption.timeInfo)
                    pumpEncryption.encodeArrayByPassword(uPacket, pumpEncryption.password)
                    pumpEncryption.encodeArrayByCfPassKey(uPacket)
                }

                EncryptionType.ENCRYPTION_RSv3,
                EncryptionType.ENCRYPTION_BLE5    -> {
                    val snEncoded = ByteArray(totalSize) { uPacket[it].toByte() }
                    return pumpEncryption.encryptSecondLevelPacket(snEncoded)
                }
            }
        }

        return ByteArray(totalSize) { uPacket[it].toByte() }
    }

    private fun getTimeBytes(): ByteArray {
        val now = Instant.fromEpochMilliseconds(pumpState.pumpTimeMillis)
        val ldt = now.toLocalDateTime(TimeZone.currentSystemDefault())
        return byteArrayOf(
            (ldt.year - 2000).toByte(), ldt.monthNumber.toByte(), ldt.dayOfMonth.toByte(),
            ldt.hour.toByte(), ldt.minute.toByte(), ldt.second.toByte()
        )
    }

    private fun sendResponse(responseBytes: ByteArray) {
        listener?.onCharacteristicChanged(responseBytes)
    }
}
