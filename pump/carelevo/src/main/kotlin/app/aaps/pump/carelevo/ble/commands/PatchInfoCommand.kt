package app.aaps.pump.carelevo.ble.commands

import app.aaps.pump.carelevo.ble.BleMultiCommand
import app.aaps.pump.carelevo.ble.BleResponse

/**
 * `CMD_PATCH_INFO_REQ` (0x33) → the patch answers with TWO report frames: RPT1 (0x93, serial) +
 * RPT2 (0x94, firmware/model). A [BleMultiCommand]: one write, both notifications collected (any
 * order) before [decode].
 *
 * Request wire format (1 byte): `[0] 0x33` — no args, no framing.
 *
 * RPT1 (0x93) wire format (≥ 15 bytes):
 * ```
 * [0]      0x93            opcode
 * [1]      resultCode
 * [2..14]  serialNumber    13 chars (each byte → ASCII char)
 * ```
 * RPT2 (0x94) wire format (real frame is 16 bytes, indices 0..15):
 * ```
 * [0]        0x94           opcode
 * [1]        resultCode
 * [2..5]     firmwareVersion 4 chars (each byte → ASCII char)
 * [11..15]   modelName       decimal-int string of each present byte (byte 10 is skipped; byte 16 is
 *                            out of range in the 16-byte frame)
 * ```
 * **Two protocol quirks:** `modelName` uses each byte's decimal value (not ASCII) and skips byte 10.
 * `bootDateTime` is NOT on the wire at all — the persistence layer stamps it from the phone clock — so
 * that mapping stays at the persistence layer, not here; this command decodes only wire bytes.
 */
class PatchInfoCommand : BleMultiCommand<PatchInfoResponse> {

    override val requestOpcode: Byte = REQUEST_OPCODE
    override val expectedResponseOpcodes: Set<Byte> = setOf(RPT1_OPCODE, RPT2_OPCODE)

    override fun encode(): ByteArray = byteArrayOf(requestOpcode)

    override fun decode(responses: Map<Byte, ByteArray>): PatchInfoResponse {
        val rpt1 = requireNotNull(responses[RPT1_OPCODE]) { "missing RPT1 (0x93)" }
        val rpt2 = requireNotNull(responses[RPT2_OPCODE]) { "missing RPT2 (0x94)" }
        require(rpt1.size >= RPT1_MIN_LENGTH) { "RPT1 too short: ${rpt1.size} < $RPT1_MIN_LENGTH" }
        require(rpt2.size >= RPT2_MIN_LENGTH) { "RPT2 too short: ${rpt2.size} < $RPT2_MIN_LENGTH" }

        // modelName is best-effort over bytes 11..16: the real 0x94 frame is 16 bytes (indices 0..15)
        // so byte 16 is absent — clamp to the frame end (a 16-byte frame → model bytes 11..15).
        val modelName =
            if (MODEL_START <= rpt2.lastIndex) rpt2.decimalString(MODEL_START, minOf(MODEL_END, rpt2.lastIndex))
            else ""

        return PatchInfoResponse(
            serialResultCode = rpt1[1].toUByte().toInt(),
            serialNumber = rpt1.asciiString(SERIAL_START, SERIAL_END),
            detailResultCode = rpt2[1].toUByte().toInt(),
            firmwareVersion = rpt2.asciiString(FIRMWARE_START, FIRMWARE_END),
            modelName = modelName
        )
    }

    /** Bytes [startInclusive]..[endInclusive] as ASCII, each byte → one char. */
    private fun ByteArray.asciiString(startInclusive: Int, endInclusive: Int): String =
        (startInclusive..endInclusive).joinToString("") { this[it].toUByte().toInt().toChar().toString() }

    /** Bytes [startInclusive]..[endInclusive] as the concatenation of each byte's DECIMAL value (protocol quirk). */
    private fun ByteArray.decimalString(startInclusive: Int, endInclusive: Int): String =
        (startInclusive..endInclusive).joinToString("") { this[it].toUByte().toInt().toString() }

    companion object {

        const val REQUEST_OPCODE: Byte = 0x33
        const val RPT1_OPCODE: Byte = 0x93.toByte()
        const val RPT2_OPCODE: Byte = 0x94.toByte()

        private const val SERIAL_START = 2
        private const val SERIAL_END = 14 // bytes 2..14 = 13 chars
        private const val FIRMWARE_START = 2
        private const val FIRMWARE_END = 5 // bytes 2..5 = 4 chars
        private const val MODEL_START = 11
        private const val MODEL_END = 16 // bytes 11..16 (byte 10 skipped)

        private const val RPT1_MIN_LENGTH = SERIAL_END + 1 // 15 — full serial required
        private const val RPT2_MIN_LENGTH = FIRMWARE_END + 1 // 6 — firmware required; modelName is best-effort (see decode)
    }
}

/**
 * Decoded response from [PatchInfoCommand] — the wire content of RPT1 (0x93) + RPT2 (0x94).
 *
 * [serialResultCode]/[detailResultCode] are the raw pump result bytes (0 = SUCCESS). `bootDateTime` is
 * intentionally absent — it is not carried on the wire; the persistence layer stamps it from the phone
 * clock.
 */
data class PatchInfoResponse(
    val serialResultCode: Int,
    val serialNumber: String,
    val detailResultCode: Int,
    val firmwareVersion: String,
    val modelName: String
) : BleResponse
