package app.aaps.pump.carelevo.ble.commands

import app.aaps.pump.carelevo.ble.BleCommand
import app.aaps.pump.carelevo.ble.BleResponse

/**
 * `CMD_MAC_ADDR_REQ` (0x3B) → `CMD_MAC_ADDR_RES` (0x9B).
 *
 * Reads the peripheral's MAC address during initial bonding. Read-only, no pump state change.
 *
 * Request wire format (2 bytes):
 * ```
 * [0] 0x3B                 opcode
 * [1] key                  caller-chosen random key, echoed in the response checksum calc
 * ```
 *
 * Response wire format (≥ 7 bytes):
 * ```
 * [0]      0x9B            opcode
 * [1..6]   macAddress      6 bytes, most-significant byte first
 * [7..]    checkSum        remainder of the payload, derived from (address || key)
 * ```
 */
class MacAddressCommand(
    /** Random byte chosen by the caller; the response's checksum is derived from this + the address. */
    private val key: Byte
) : BleCommand<MacAddressResponse> {

    override val requestOpcode: Byte = REQUEST_OPCODE
    override val expectedResponseOpcode: Byte = RESPONSE_OPCODE

    override fun encode(): ByteArray = byteArrayOf(requestOpcode, key)

    override fun decode(responsePayload: ByteArray): MacAddressResponse {
        require(responsePayload.isNotEmpty() && responsePayload[0] == expectedResponseOpcode) {
            "expected opcode 0x${"%02X".format(expectedResponseOpcode)}, got " +
                (responsePayload.getOrNull(0)?.let { "0x${"%02X".format(it)}" } ?: "empty")
        }
        require(responsePayload.size >= MIN_LENGTH) {
            "response too short: ${responsePayload.size} bytes, need at least $MIN_LENGTH"
        }

        val macHex = responsePayload.copyOfRange(ADDRESS_START, ADDRESS_END).toUppercaseHex()
        val checkSumHex = responsePayload.copyOfRange(CHECKSUM_START, responsePayload.size).toUppercaseHex()
        return MacAddressResponse(macAddress = macHex, checkSum = checkSumHex)
    }

    private fun ByteArray.toUppercaseHex(): String =
        joinToString(separator = "") { "%02X".format(it) }

    companion object {

        const val REQUEST_OPCODE: Byte = 0x3B
        const val RESPONSE_OPCODE: Byte = 0x9B.toByte()

        private const val ADDRESS_START = 1
        private const val ADDRESS_END = 7 // exclusive — bytes 1..6 inclusive
        private const val CHECKSUM_START = 7
        private const val MIN_LENGTH = CHECKSUM_START + 1 // opcode + 6 address bytes + ≥1 checksum byte
    }
}

/**
 * Decoded response from [MacAddressCommand].
 *
 * Both fields are upper-case hex strings with no separators (e.g. `"94B2161D2F6D"`).
 * The consumer is responsible for any display formatting.
 */
data class MacAddressResponse(
    val macAddress: String,
    val checkSum: String
) : BleResponse
