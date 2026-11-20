package app.aaps.plugins.libre3.nfc

import app.aaps.plugins.libre3.libre3AsciiToString
import app.aaps.plugins.libre3.libre3HexLower
import app.aaps.plugins.libre3.libre3HexUpper

/**
 * Patch info read with the `A1` command.
 *
 * Ported from LibreCRKit `NFCActivationModels.swift` (`Libre3NFCPatchInfo`) at pin `a86b92f`.
 * The Swift parser is the truth for these offsets, not the overview in `protocol.md`.
 *
 * @param generation 0 is Libre 3, 1 is Libre 3 Plus.
 * @param wearDurationMinutes whole sensor life, about 20160 for Libre 3 and 21600 for Libre 3 Plus.
 * @param warmupMinutes warm-up length. The raw byte counts steps of five minutes.
 * @param state `0x01` means the sensor was never activated. Anything else means it is running.
 * @param serialNumber nine characters of text.
 */
data class Libre3PatchInfo(
    val generation: Int,
    val wearDurationMinutes: Int,
    val warmupMinutes: Int,
    val productType: Int,
    val firmwareVersion: String,
    val state: Byte,
    val serialNumber: String,
) {

    /** True for Libre 3 Plus and Instinct, false for Libre 3. */
    val isPlus: Boolean get() = generation >= 1

    /** `A0` for a sensor that was never activated, `A8` for a sensor that already runs. */
    val recommendedCommand: Byte
        get() = if (state == Libre3NfcUids.STATE_FRESH) Libre3NfcUids.CMD_ACTIVATE else Libre3NfcUids.CMD_SWITCH_RECEIVER
}

/**
 * Answer to the `A0` or `A8` command. This is where the BLE address and the PIN come from.
 *
 * Ported from LibreCRKit `NFCActivationModels.swift` (`Libre3NFCActivationResponse`).
 *
 * @param bleAddressLittleEndian six address bytes in the order the sensor sends them.
 * @param blePin four bytes. They are used as the tail of the pairing message.
 * @param activationTimeRaw four bytes. All zero on a fresh activation. On a sensor that was
 *   already running, this is the moment the sensor was first activated, not the time this phone
 *   just sent. It is the only honest anchor for sample times of a sensor taken over mid-life.
 */
data class Libre3ActivationResponse(
    val bleAddressLittleEndian: ByteArray,
    val blePin: ByteArray,
    val activationTimeRaw: ByteArray,
) {

    init {
        require(bleAddressLittleEndian.size == ADDRESS_SIZE) { "the Bluetooth address must be 6 bytes" }
        require(blePin.size == PIN_SIZE) { "the PIN must be 4 bytes" }
        require(activationTimeRaw.size == TIME_SIZE) { "the activation time must be 4 bytes" }
    }

    /** Bluetooth address the way Android writes it: reversed, upper case, split by colons. */
    val bleAddressDisplay: String
        get() = bleAddressLittleEndian.reversed().joinToString(":") { it.libre3HexUpper() }

    /**
     * Moment the sensor was activated, in whole seconds since 1970, or 0 when the sensor did not
     * report one. A fresh sensor always answers with zero here.
     */
    val activationEpochSeconds: Long
        get() = (activationTimeRaw[0].toLong() and 0xFF) or
            ((activationTimeRaw[1].toLong() and 0xFF) shl 8) or
            ((activationTimeRaw[2].toLong() and 0xFF) shl 16) or
            ((activationTimeRaw[3].toLong() and 0xFF) shl 24)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Libre3ActivationResponse) return false
        return bleAddressLittleEndian.contentEquals(other.bleAddressLittleEndian) &&
            blePin.contentEquals(other.blePin) &&
            activationTimeRaw.contentEquals(other.activationTimeRaw)
    }

    override fun hashCode(): Int {
        var result = bleAddressLittleEndian.contentHashCode()
        result = 31 * result + blePin.contentHashCode()
        result = 31 * result + activationTimeRaw.contentHashCode()
        return result
    }

    companion object {

        private const val ADDRESS_SIZE = 6
        private const val PIN_SIZE = 4
        private const val TIME_SIZE = 4
    }
}

/**
 * Why an NFC scan did not work, as a value the user interface can turn into its own text.
 *
 * The driver module has no string resources, so it must never hand a ready made sentence to the
 * screen. The screen picks a translated string for each of these instead.
 */
enum class Libre3NfcFailure {

    /** The phone touched a tag that is not a Libre 3 sensor. */
    NOT_A_LIBRE3_SENSOR,

    /** The sensor answered, but it refused the command. */
    SENSOR_REFUSED,

    /** The phone lost the tag, usually because it moved away too early. */
    LINK_LOST,

    /** This phone could not store what it needs, so nothing was sent to the sensor. */
    PHONE_STORAGE_FAILED,

    /**
     * The other slot already holds this sensor, so the scan was stopped before anything was sent.
     *
     * One physical sensor may only be held by one slot. The check runs before the activation
     * command, so the sensor keeps its PIN and the slot that holds it keeps working.
     */
    SAME_SENSOR_OTHER_SLOT
}

/**
 * Raised when the NFC step cannot go on.
 *
 * The message is for the log only. The screen must use [failure] and pick its own translated text.
 */
class Libre3NfcException(
    message: String,
    val failure: Libre3NfcFailure = Libre3NfcFailure.LINK_LOST,
) : Exception(message)

/**
 * Builds the ISO 15693 command frames and reads the answers.
 *
 * Everything here is pure, so it is unit tested with the byte vectors that come from the
 * LibreCRKit tests (`Tests/LibreCRKitTests/NFCActivationCommandTests.swift`).
 */
object Libre3NfcCommands {

    private const val PATCH_INFO_MIN_LENGTH = 29
    private const val ACTIVATION_RESPONSE_LENGTH = 19

    /** `02 A1 7A`, no body. */
    fun patchInfoFrame(): ByteArray =
        byteArrayOf(Libre3NfcUids.REQUEST_FLAG, Libre3NfcUids.CMD_PATCH_INFO, Libre3NfcUids.MANUFACTURER)

    /**
     * `02 <command> 7A` plus the ten byte body.
     *
     * @param command must be [Libre3NfcUids.CMD_ACTIVATE] or [Libre3NfcUids.CMD_SWITCH_RECEIVER].
     * @param timeSeconds phone time in whole seconds since 1970.
     * @param receiverId identifier of this phone, see [receiverIdFrom].
     */
    fun activationFrame(command: Byte, timeSeconds: Long, receiverId: Int): ByteArray {
        require(command == Libre3NfcUids.CMD_ACTIVATE || command == Libre3NfcUids.CMD_SWITCH_RECEIVER) {
            "unknown activation command"
        }
        return byteArrayOf(Libre3NfcUids.REQUEST_FLAG, command, Libre3NfcUids.MANUFACTURER) +
            activationBody(timeSeconds, receiverId)
    }

    /** `timeSeconds` and `receiverId`, both little endian, then their CRC, also little endian. */
    fun activationBody(timeSeconds: Long, receiverId: Int): ByteArray {
        val body = littleEndian4(timeSeconds.toInt()) + littleEndian4(receiverId)
        val crc = abbottCrc16(body)
        return body + byteArrayOf((crc and 0xFF).toByte(), ((crc shr 8) and 0xFF).toByte())
    }

    /**
     * CRC used by the sensor. It is a CRC-16/CCITT with two changes: every input byte is read with
     * its bits in the opposite order, and the result is not inverted at the end.
     */
    fun abbottCrc16(data: ByteArray): Int {
        var crc = 0xFFFF
        for (byte in data) {
            crc = crc xor (reverseBits(byte) shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1
                crc = crc and 0xFFFF
            }
        }
        return crc and 0xFFFF
    }

    /**
     * Identifier this phone sends to the sensor, built from a text that is unique to the install.
     *
     * The number is folded the same way as in LibreCRKit `Libre3ReceiverID.accountlessValue`: one
     * multiply that is allowed to overflow, then exclusive or with the next unit of the text. In
     * Kotlin an `Int` overflows in exactly the same way as the unsigned 32 bit value in Swift, so
     * the bits match.
     */
    fun receiverIdFrom(uniqueId: String): Int {
        var value = 0
        for (unit in uniqueId) {
            value = (value * 0x811C9DC5.toInt()) xor unit.code
        }
        return value
    }

    /** The four bytes of a receiver id as they travel on the wire. */
    fun receiverIdBytes(receiverId: Int): ByteArray = littleEndian4(receiverId)

    /**
     * Reads the answer to `A1`.
     *
     * Some readers hand back the answer without the leading zero flag, and some pad it with extra
     * `A5` bytes. Both shapes are accepted and cleaned up first, exactly as the Swift parser does.
     */
    fun parsePatchInfo(raw: ByteArray): Libre3PatchInfo {
        val frame = normalizePatchInfo(raw)
        if (frame.size < PATCH_INFO_MIN_LENGTH || frame[0] != 0x00.toByte() || frame[1] != 0xA5.toByte()) {
            throw Libre3NfcException(
                "patch info not readable, ${raw.size} bytes ${hexHead(raw)}",
                Libre3NfcFailure.NOT_A_LIBRE3_SENSOR,
            )
        }
        val serialBytes = frame.copyOfRange(18, 27)
        return Libre3PatchInfo(
            generation = u16(frame, 7),
            wearDurationMinutes = u16(frame, 9),
            warmupMinutes = (frame[16].toInt() and 0xFF) * 5,
            productType = frame[15].toInt() and 0xFF,
            firmwareVersion = "${byteAt(frame, 14)}.${byteAt(frame, 13)}.${byteAt(frame, 12)}.${byteAt(frame, 11)}",
            state = frame[17],
            serialNumber = serialBytes.libre3AsciiToString(),
        )
    }

    /** Reads the answer to `A0` or `A8`. */
    fun parseActivationResponse(raw: ByteArray): Libre3ActivationResponse {
        val frame = normalizeActivationResponse(raw)
        if (frame.size != ACTIVATION_RESPONSE_LENGTH ||
            frame[0] != 0x00.toByte() ||
            frame[1] != 0xA5.toByte() ||
            frame[2] != 0x00.toByte()
        ) {
            throw Libre3NfcException("activation answer not readable, ${raw.size} bytes", Libre3NfcFailure.SENSOR_REFUSED)
        }
        return Libre3ActivationResponse(
            bleAddressLittleEndian = frame.copyOfRange(3, 9),
            blePin = frame.copyOfRange(9, 13),
            activationTimeRaw = frame.copyOfRange(13, 17),
        )
    }

    /**
     * Checks that a frame we are about to send, or that a test feeds back to us, really carries the
     * sensor's manufacturer code. A frame for another manufacturer must never be answered.
     */
    fun isForThisManufacturer(frame: ByteArray): Boolean =
        frame.size >= 3 && frame[2] == Libre3NfcUids.MANUFACTURER

    private fun normalizePatchInfo(raw: ByteArray): ByteArray {
        var frame = raw
        if (frame.isNotEmpty() && frame[0] == 0xA5.toByte()) frame = byteArrayOf(0x00) + frame
        val prefixAt = indexOfPatchInfoPrefix(frame)
        if (prefixAt > 0) frame = frame.copyOfRange(prefixAt, frame.size)
        if (frame.size >= 2 && frame[0] == 0x00.toByte() && frame[1] == 0xA5.toByte()) {
            var bodyStart = 2
            while (bodyStart < frame.size && frame[bodyStart] == 0xA5.toByte()) bodyStart++
            if (bodyStart > 2) return byteArrayOf(0x00, 0xA5.toByte()) + frame.copyOfRange(bodyStart, frame.size)
        }
        return frame
    }

    /**
     * Some readers put extra ISO 15693 bytes in front of the real `00 A5` body. A 65 byte answer
     * that still contains that prefix is accepted by slicing from the first match.
     */
    private fun indexOfPatchInfoPrefix(frame: ByteArray): Int {
        if (frame.size < 2) return -1
        for (index in 0 until frame.size - 1) {
            if (frame[index] == 0x00.toByte() && frame[index + 1] == 0xA5.toByte()) return index
        }
        return -1
    }

    private fun hexHead(raw: ByteArray, maxBytes: Int = 40): String =
        raw.take(maxBytes).joinToString("") { it.libre3HexLower() }

    private fun normalizeActivationResponse(raw: ByteArray): ByteArray {
        if (raw.size >= 2 && raw[0] == 0x00.toByte() && raw[1] == 0xA5.toByte()) return raw
        if (raw.isNotEmpty() && raw[0] == 0xA5.toByte()) return byteArrayOf(0x00) + raw
        return raw
    }

    private fun littleEndian4(value: Int): ByteArray =
        byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte(),
        )

    private fun u16(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

    private fun byteAt(data: ByteArray, offset: Int): Int = data[offset].toInt() and 0xFF

    private fun reverseBits(value: Byte): Int {
        var x = value.toInt() and 0xFF
        var out = 0
        repeat(8) {
            out = (out shl 1) or (x and 1)
            x = x shr 1
        }
        return out
    }

    /**
     * Checks the answer flag of one ISO 15693 answer and hands the answer back unchanged.
     *
     * The first byte is the answer flag. Bit 0 set means the sensor refused the command, and the
     * byte after it says why. This is protocol, not platform: it is the same check whichever radio
     * carried the bytes.
     *
     * @throws Libre3NfcException on an empty answer or on a refusal.
     */
    fun checkAnswerFlag(answer: ByteArray): ByteArray {
        if (answer.isEmpty()) {
            throw Libre3NfcException("the sensor sent an empty answer", Libre3NfcFailure.LINK_LOST)
        }
        if (answer[0].toInt() and 0x01 != 0) {
            val code = if (answer.size > 1) "0x${answer[1].libre3HexUpper()}" else "unknown"
            throw Libre3NfcException("the sensor refused the command, error $code", Libre3NfcFailure.SENSOR_REFUSED)
        }
        return answer
    }
}
