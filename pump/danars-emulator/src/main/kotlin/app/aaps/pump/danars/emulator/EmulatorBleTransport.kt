package app.aaps.pump.danars.emulator

import app.aaps.pump.danars.encryption.BleEncryption
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

        // Accumulate chunks into buffer
        addToBuffer(data)

        // Try to parse a complete packet
        val packet = extractPacket() ?: return

        // Process the packet through pump-side encryption and emulator
        val response = processPumpSide(packet)
        if (response != null) {
            // Send response back to BLEComm (may need chunking for large responses)
            sendResponse(response)
        }
    }

    private fun addToBuffer(data: ByteArray) {
        synchronized(readBuffer) {
            System.arraycopy(data, 0, readBuffer, bufferLength, data.size)
            bufferLength += data.size
        }
    }

    private fun extractPacket(): ByteArray? {
        synchronized(readBuffer) {
            if (bufferLength < 6) return null

            // Find packet start [A5 A5]
            val startByte = 0xA5.toByte()
            val endByte = 0x5A.toByte()

            if (readBuffer[0] != startByte || readBuffer[1] != startByte) {
                // Not a valid packet start, clear buffer
                bufferLength = 0
                return null
            }

            val length = readBuffer[2].toInt() and 0xFF
            val totalLength = length + 7

            if (bufferLength < totalLength) return null // need more data

            // Check end bytes
            if (readBuffer[totalLength - 2] != endByte || readBuffer[totalLength - 1] != endByte) {
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

            return packet
        }
    }

    /**
     * Process a complete encrypted packet from the app side.
     * Decrypt it, extract the command, run it through the emulator,
     * and build an encrypted response.
     */
    private fun processPumpSide(encryptedPacket: ByteArray): ByteArray? {
        // Decrypt using pump-side encryption
        val decrypted = pumpEncryption.getDecryptedPacket(encryptedPacket) ?: return null

        val type = decrypted[0].toInt() and 0xFF
        val opCode = decrypted[1].toInt() and 0xFF

        return when (type) {
            BleEncryption.DANAR_PACKET__TYPE_ENCRYPTION_REQUEST.toInt() -> handleEncryptionRequest(opCode, decrypted)
            BleEncryption.DANAR_PACKET__TYPE_COMMAND.toInt()            -> handleCommand(opCode, decrypted)
            else                                                        -> null
        }
    }

    /**
     * Handle encryption handshake packets from the app.
     */
    private fun handleEncryptionRequest(opCode: Int, decrypted: ByteArray): ByteArray {
        return when (opCode) {
            BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__PUMP_CHECK     -> {
                // Respond "OK" for v1 (DEFAULT encryption, 4-byte response)
                buildEncryptionResponse(opCode, byteArrayOf('O'.code.toByte(), 'K'.code.toByte()))
            }

            BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__CHECK_PASSKEY  -> {
                // Extract passkey from decrypted data and set on pump-side encryption
                // The app-side sets cfPassKey when it receives the OK response,
                // so pump-side must match for subsequent command encoding/decoding
                if (decrypted.size > 3) {
                    pumpEncryption.passKey[0] = decrypted[2].toUByte()
                    pumpEncryption.passKey[1] = decrypted[3].toUByte()
                    pumpEncryption.cfPassKey[0] = decrypted[2].toUByte()
                    pumpEncryption.cfPassKey[1] = decrypted[3].toUByte()
                }
                // Passkey OK - no new pairing needed
                buildEncryptionResponse(opCode, byteArrayOf(0x00))
            }

            BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__TIME_INFORMATION -> {
                // Respond with time info + encoded password
                // Password encoding: password XOR 3463 → split into 2 bytes
                val pass = pumpState.pumpPassword.toInt(16) xor 3463
                val passLow = (pass and 0xFF).toByte()
                val passHigh = ((pass shr 8) and 0xFF).toByte()
                // Response: time bytes + password
                val timeBytes = getTimeBytes()
                val responseData = timeBytes + byteArrayOf(passLow, passHigh)

                // Update pump-side encryption state to match what app side will have
                // after it decrypts this response (so subsequent packets can be decoded)
                pumpEncryption.connectionState = 2
                for (i in 0..5) pumpEncryption.timeInfo[i] = timeBytes[i].toUByte()
                // Password is stored XOR'd with 0x87, 0x0D on both sides
                pumpEncryption.password[0] = (passLow.toUByte() xor 0x87u).toUByte()
                pumpEncryption.password[1] = (passHigh.toUByte() xor 0x0Du).toUByte()

                buildEncryptionResponse(opCode, responseData)
            }

            BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__PASSKEY_REQUEST -> {
                // Pairing accepted
                buildEncryptionResponse(
                    BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__PASSKEY_REQUEST,
                    byteArrayOf(0x00)
                )
            }

            BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__PASSKEY_RETURN -> {
                // Return pairing key
                buildEncryptionResponse(
                    BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__PASSKEY_RETURN,
                    pumpState.pairingKey
                )
            }

            else -> buildEncryptionResponse(opCode, byteArrayOf(0x00))
        }
    }

    /**
     * Handle regular command packets from the app.
     */
    private fun handleCommand(opCode: Int, decrypted: ByteArray): ByteArray {
        // Extract params from decrypted buffer (DATA_START = 2)
        val params = if (decrypted.size > 2) {
            decrypted.copyOfRange(2, decrypted.size)
        } else {
            ByteArray(0)
        }

        // Process through emulator
        val responseData = emulator.processCommand(opCode, params)

        // Build response packet
        return buildCommandResponse(opCode, responseData)
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
     * After handshake (connectionState 2), full encoding is applied.
     */
    private fun buildCommandResponse(opCode: Int, data: ByteArray): ByteArray {
        return buildRawPacket(BleEncryption.DANAR_PACKET__TYPE_RESPONSE, opCode, data, applyFullEncoding = pumpEncryption.connectionState == 2)
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
