package app.aaps.pump.danars.emulator

import app.aaps.pump.danars.encryption.BleEncryption
import app.aaps.pump.danars.encryption.EncryptionType
import app.aaps.pump.danars.services.BleTransport
import app.aaps.pump.danars.services.BleTransportListener
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * BleTransport implementation that emulates a Dana RS pump.
 *
 * Instead of talking to a real Bluetooth device, this routes data through
 * a [PumpEmulator] with a pump-side [BleEncryption] instance for the
 * encryption handshake.
 *
 * The flow for each write:
 * 1. App writes encrypted bytes → [writeCharacteristic]
 * 2. Pump-side decrypts → extracts opcode + params
 * 3. [PumpEmulator] processes command → generates response data
 * 4. Pump-side encrypts response → calls [BleTransportListener.onCharacteristicChanged]
 * 5. BLEComm decrypts and routes to packet handler
 *
 * Supports DEFAULT (v1), RSv3, and BLE5 encryption modes.
 * Threading: All operations are synchronous (no BLE delays).
 * This makes tests fast and deterministic.
 */
class EmulatorBleTransport(
    val emulator: PumpEmulator = PumpEmulator(),
    private val deviceName: String = "UHH00002TI"
) : BleTransport {

    /** Pump-side encryption instance (mirrors the app-side BleEncryption) */
    val pumpEncryption = BleEncryption().also { enc ->
        // Set the device name on the pump side so SN encoding/decoding works
        for (i in deviceName.indices) {
            if (i < 10) enc.deviceName[i] = deviceName[i].code.toUByte()
        }
    }

    private var listener: BleTransportListener? = null
    private var connected = false

    // Read buffer for reassembling chunked packets
    private val readBuffer = ByteArray(1024)
    private var bufferLength = 0

    val pumpState: PumpState get() = emulator.state

    override fun setListener(listener: BleTransportListener?) {
        this.listener = listener
    }

    override fun getDeviceName(address: String): String = deviceName

    override fun isDeviceBonded(address: String): Boolean = true

    override fun createBond(address: String): Boolean = true

    override fun removeBond(address: String) {}

    override fun connectGatt(address: String): Boolean {
        connected = true
        // Simulate the connection flow asynchronously:
        // onConnected → discoverServices is called by BLEComm
        listener?.onConnectionStateChanged(true)
        return true
    }

    override fun disconnectGatt() {
        connected = false
        // Don't fire onConnectionStateChanged here - BLEComm manages its own state on disconnect
    }

    override fun closeGatt() {
        connected = false
    }

    override fun discoverServices() {
        // Immediately report services discovered
        listener?.onServicesDiscovered(true)
    }

    override fun findCharacteristics(): Boolean = true

    override fun enableNotifications() {
        // Immediately report descriptor written (triggers sendConnect in BLEComm)
        listener?.onDescriptorWritten()
    }

    override fun writeCharacteristic(data: ByteArray) {
        // Notify that write completed (triggers next chunk from queue)
        listener?.onCharacteristicWritten()

        // For RSv3/BLE5, decrypt second-level before buffering
        // (matching BLEComm.readDataParsing which decrypts before adding to buffer)
        val processedData = if (needsSecondLevelDecryption()) {
            pumpEncryption.decryptSecondLevelPacket(data)
        } else {
            data
        }

        // Accumulate chunks into buffer
        addToBuffer(processedData)

        // Try to parse a complete packet
        val packet = extractPacket() ?: return

        // Process the packet through pump-side encryption and emulator
        processPumpSide(packet)
    }

    private fun needsSecondLevelDecryption(): Boolean =
        pumpEncryption.connectionState == 2 &&
            pumpEncryption.securityVersion != EncryptionType.ENCRYPTION_DEFAULT

    private fun needsSecondLevelEncryption(): Boolean = needsSecondLevelDecryption()

    private fun addToBuffer(data: ByteArray) {
        synchronized(readBuffer) {
            System.arraycopy(data, 0, readBuffer, bufferLength, data.size)
            bufferLength += data.size
        }
    }

    private fun extractPacket(): ByteArray? {
        synchronized(readBuffer) {
            if (bufferLength < 6) return null

            // Find packet start [A5 A5] or [AA AA] (BLE5 after decryptSecondLevelPacket)
            val isStandard = readBuffer[0] == 0xA5.toByte() && readBuffer[1] == 0xA5.toByte()
            val isBle5 = readBuffer[0] == 0xAA.toByte() && readBuffer[1] == 0xAA.toByte()

            if (!isStandard && !isBle5) {
                // Not a valid packet start, clear buffer
                bufferLength = 0
                return null
            }

            val length = readBuffer[2].toInt() and 0xFF
            val totalLength = length + 7

            if (bufferLength < totalLength) return null // need more data

            // Check end bytes (5A for standard, EE for BLE5)
            val expectedEnd = if (isBle5) 0xEE.toByte() else 0x5A.toByte()
            if (readBuffer[totalLength - 2] != expectedEnd || readBuffer[totalLength - 1] != expectedEnd) {
                bufferLength = 0
                return null
            }

            // Extract packet
            val packet = ByteArray(totalLength)
            System.arraycopy(readBuffer, 0, packet, 0, totalLength)

            // Remove from buffer
            val remaining = bufferLength - totalLength
            if (remaining > 0) {
                System.arraycopy(readBuffer, totalLength, readBuffer, 0, remaining)
            }
            bufferLength = remaining

            // Normalize BLE5 markers to standard A5/5A for getDecryptedPacket
            if (isBle5) {
                packet[0] = 0xA5.toByte()
                packet[1] = 0xA5.toByte()
                packet[totalLength - 2] = 0x5A.toByte()
                packet[totalLength - 1] = 0x5A.toByte()
            }

            return packet
        }
    }

    /**
     * Process a complete packet from the app side.
     * For RSv3/BLE5, second-level decryption was already applied in writeCharacteristic.
     */
    private fun processPumpSide(packet: ByteArray) {
        // Decrypt using pump-side encryption (SN + CRC check, time/password for DEFAULT)
        val decrypted = pumpEncryption.getDecryptedPacket(packet) ?: return

        val type = decrypted[0].toInt() and 0xFF
        val opCode = decrypted[1].toInt() and 0xFF

        when (type) {
            BleEncryption.DANAR_PACKET__TYPE_ENCRYPTION_REQUEST.toInt() -> {
                val response = handleEncryptionRequest(opCode, decrypted)
                sendResponse(response)
            }

            BleEncryption.DANAR_PACKET__TYPE_COMMAND.toInt()            -> handleCommand(opCode, decrypted)
        }
    }

    /**
     * Handle encryption handshake packets from the app.
     */
    private fun handleEncryptionRequest(opCode: Int, decrypted: ByteArray): ByteArray {
        return when (opCode) {
            BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__PUMP_CHECK     -> {
                when (pumpState.pumpCheckResponse) {
                    PumpCheckResponse.OK         -> buildPumpCheckOkResponse(opCode)
                    PumpCheckResponse.PUMP_ERROR -> buildEncryptionResponse(opCode, "PUMP".toByteArray(Charsets.US_ASCII))
                    PumpCheckResponse.BUSY       -> buildEncryptionResponse(opCode, "BUSY".toByteArray(Charsets.US_ASCII))
                }
            }

            BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__CHECK_PASSKEY  -> {
                // Extract passkey from decrypted data and set on pump-side encryption
                if (decrypted.size > 3) {
                    pumpEncryption.passKey[0] = decrypted[2].toUByte()
                    pumpEncryption.passKey[1] = decrypted[3].toUByte()
                    pumpEncryption.cfPassKey[0] = decrypted[2].toUByte()
                    pumpEncryption.cfPassKey[1] = decrypted[3].toUByte()
                }
                buildEncryptionResponse(opCode, byteArrayOf(0x00))
            }

            BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__TIME_INFORMATION -> {
                handleTimeInformation(opCode)
            }

            BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__PASSKEY_REQUEST -> {
                buildEncryptionResponse(
                    BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__PASSKEY_REQUEST,
                    byteArrayOf(0x00)
                )
            }

            BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__PASSKEY_RETURN -> {
                buildEncryptionResponse(
                    BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__PASSKEY_RETURN,
                    pumpState.pairingKey
                )
            }

            else -> buildEncryptionResponse(opCode, byteArrayOf(0x00))
        }
    }

    /**
     * Build PUMP_CHECK OK response based on hardware model.
     * Response size determines encryption mode:
     * - 2 bytes data (4 total) → DEFAULT v1
     * - 7 bytes data (9 total) → RSv3
     * - 12 bytes data (14 total) → BLE5
     */
    private fun buildPumpCheckOkResponse(opCode: Int): ByteArray {
        return when (pumpState.hwModel) {
            0x05, 0x06 -> {
                // RSv3 — 7 bytes data
                buildEncryptionResponse(opCode, byteArrayOf(
                    'O'.code.toByte(), 'K'.code.toByte(), 0,
                    pumpState.hwModel.toByte(), 0,
                    pumpState.protocol.toByte(),
                    pumpState.rsv3RandomSyncKey
                ))
            }

            0x09, 0x0A -> {
                // BLE5 — 12 bytes data
                val key = pumpState.ble5PairingKey.toByteArray(Charsets.US_ASCII)
                buildEncryptionResponse(opCode, byteArrayOf(
                    'O'.code.toByte(), 'K'.code.toByte(), 0,
                    pumpState.hwModel.toByte(), 0,
                    pumpState.protocol.toByte()
                ) + key)
            }

            else -> {
                // DEFAULT v1 — 2 bytes data
                buildEncryptionResponse(opCode, byteArrayOf('O'.code.toByte(), 'K'.code.toByte()))
            }
        }
    }

    /**
     * Handle TIME_INFORMATION based on encryption mode.
     * Must build response BEFORE updating encryption state,
     * because CRC formula changes with securityVersion + connectionState.
     */
    private fun handleTimeInformation(opCode: Int): ByteArray {
        return when (pumpState.hwModel) {
            0x05, 0x06 -> {
                // RSv3: respond with success, then setup pairing keys
                val response = buildEncryptionResponse(opCode, byteArrayOf(0x00))
                // Setup pump-side encryption for subsequent commands
                pumpEncryption.setEnhancedEncryption(EncryptionType.ENCRYPTION_RSv3)
                pumpEncryption.setPairingKeys(
                    pumpState.rsv3PairingKey,
                    pumpState.rsv3RandomPairingKey,
                    pumpState.rsv3RandomSyncKey
                )
                pumpEncryption.connectionState = 2
                response
            }

            0x09, 0x0A -> {
                // BLE5: respond with success, then setup BLE5 key
                val response = buildEncryptionResponse(opCode, byteArrayOf(0x00))
                pumpEncryption.setEnhancedEncryption(EncryptionType.ENCRYPTION_BLE5)
                pumpEncryption.setBle5Key(pumpState.ble5PairingKey.toByteArray(Charsets.US_ASCII))
                pumpEncryption.connectionState = 2
                response
            }

            else       -> {
                // DEFAULT v1: respond with time + password
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
        }
    }

    /**
     * Handle regular command packets from the app.
     * Supports multiple responses (e.g., history events) and notification packets.
     */
    private fun handleCommand(opCode: Int, decrypted: ByteArray) {
        val params = if (decrypted.size > 2) {
            decrypted.copyOfRange(2, decrypted.size)
        } else {
            ByteArray(0)
        }

        val responses = emulator.processCommandMulti(opCode, params)

        for (responseData in responses) {
            val response = buildCommandResponse(opCode, responseData)
            sendResponse(response)
        }

        for (notify in emulator.pendingNotifications) {
            val response = buildNotifyPacket(notify.opCode, notify.data)
            sendResponse(response)
        }
        emulator.pendingNotifications.clear()
    }

    /**
     * Build an encryption response packet.
     * During handshake (connectionState 0), only SN encoding is applied.
     */
    private fun buildEncryptionResponse(opCode: Int, data: ByteArray): ByteArray {
        return buildRawPacket(BleEncryption.DANAR_PACKET__TYPE_ENCRYPTION_RESPONSE.toInt(), opCode, data, applyFullEncoding = false)
    }

    /**
     * Build a command response packet.
     * For DEFAULT: applies SN + time/password encoding.
     * For RSv3/BLE5: applies SN + second-level encryption.
     */
    private fun buildCommandResponse(opCode: Int, data: ByteArray): ByteArray {
        val isDefault = pumpEncryption.securityVersion == EncryptionType.ENCRYPTION_DEFAULT
        val applyFullEncoding = pumpEncryption.connectionState == 2 && isDefault
        var packet = buildRawPacket(BleEncryption.DANAR_PACKET__TYPE_RESPONSE, opCode, data, applyFullEncoding)
        if (pumpEncryption.connectionState == 2 && !isDefault) {
            packet = pumpEncryption.encryptSecondLevelPacket(packet)
        }
        return packet
    }

    /**
     * Build a notification packet (TYPE_NOTIFY).
     * Used for unsolicited messages like delivery progress and alarms.
     */
    private fun buildNotifyPacket(opCode: Int, data: ByteArray): ByteArray {
        val isDefault = pumpEncryption.securityVersion == EncryptionType.ENCRYPTION_DEFAULT
        val applyFullEncoding = pumpEncryption.connectionState == 2 && isDefault
        var packet = buildRawPacket(BleEncryption.DANAR_PACKET__TYPE_NOTIFY, opCode, data, applyFullEncoding)
        if (pumpEncryption.connectionState == 2 && !isDefault) {
            packet = pumpEncryption.encryptSecondLevelPacket(packet)
        }
        return packet
    }

    /**
     * Build an encrypted response packet.
     * Format: [A5 A5] [LEN] [TYPE] [OPCODE] [DATA...] [CRC_HI] [CRC_LO] [5A 5A]
     */
    private fun buildRawPacket(type: Int, opCode: Int, data: ByteArray, applyFullEncoding: Boolean): ByteArray {
        val len = 2 + data.size // TYPE + OPCODE + DATA
        val totalSize = 2 + 1 + len + 2 + 2 // START(2) + LENGTH(1) + content + CRC(2) + END(2)
        val packet = ByteArray(totalSize)

        packet[0] = 0xA5.toByte()
        packet[1] = 0xA5.toByte()
        packet[2] = len.toByte()
        packet[3] = type.toByte()
        packet[4] = opCode.toByte()
        System.arraycopy(data, 0, packet, 5, data.size)

        // Calculate CRC over [TYPE][OPCODE][DATA]
        val crcData = UByteArray(len)
        for (i in 0 until len) {
            crcData[i] = packet[3 + i].toUByte()
        }
        val crc = pumpEncryption.generateCrc(crcData)
        packet[totalSize - 4] = (crc.toInt() shr 8).toByte()
        packet[totalSize - 3] = crc.toByte()
        packet[totalSize - 2] = 0x5A.toByte()
        packet[totalSize - 1] = 0x5A.toByte()

        // Encode with serial number (always applied)
        val uPacket = UByteArray(totalSize) { packet[it].toUByte() }
        pumpEncryption.encodeArrayBySn(uPacket)
        if (applyFullEncoding) {
            pumpEncryption.encodeArrayByTime(uPacket, pumpEncryption.timeInfo)
            pumpEncryption.encodeArrayByPassword(uPacket, pumpEncryption.password)
            pumpEncryption.encodeArrayByCfPassKey(uPacket)
        }

        return ByteArray(totalSize) { uPacket[it].toByte() }
    }

    private fun getTimeBytes(): ByteArray {
        val now = Instant.fromEpochMilliseconds(pumpState.pumpTimeMillis)
        val ldt = now.toLocalDateTime(TimeZone.currentSystemDefault())
        return byteArrayOf(
            (ldt.year - 2000).toByte(),
            ldt.monthNumber.toByte(),
            ldt.dayOfMonth.toByte(),
            ldt.hour.toByte(),
            ldt.minute.toByte(),
            ldt.second.toByte()
        )
    }

    /**
     * Send response bytes back to BLEComm, chunked to 20-byte MTU if needed.
     */
    private fun sendResponse(responseBytes: ByteArray) {
        // BLE MTU is 20 bytes, but for the emulator we can send the full packet
        // since BLEComm's readDataParsing handles buffering
        listener?.onCharacteristicChanged(responseBytes)
    }
}
