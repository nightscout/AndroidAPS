package app.aaps.pump.carelevo.ble.commands

import app.aaps.pump.carelevo.ble.BleCommand

/**
 * Basal-program write (v2 encoding). One command class for both:
 * - [isUpdate] = `false`: `CMD_BASAL_PROGRAM_REQ1` (0x13) → `RES1` (0x73) — set the initial program (activation)
 * - [isUpdate] = `true`:  `CMD_BASAL_CHANGE_REQ1`  (0x21) → `RES1` (0x81) — update an existing program
 *
 * A full basal program is sent as **three sequential [BasalProgramCommand]s** ([seqNo] 0, 1, 2) — that
 * orchestration lives in the caller, not here (this is NOT a multi-response command; each write gets its
 * own single response).
 *
 * Request wire format: `[0] opcode, [1] seqNo, [2..] segments` where each segment is `[speedInt, speedCenti]`
 * (v2 `BasalProgramToByteV2` = `encodeUnitCenti(injectSpeed)`, HALF_UP; no hour/min bytes in v2).
 * Response: `[0] opcode, [1] resultCode` → [SimpleResultResponse].
 */
class BasalProgramCommand(
    private val isUpdate: Boolean,
    private val seqNo: Int,
    private val segmentSpeeds: List<Double>
) : BleCommand<SimpleResultResponse> {

    init {
        require(seqNo in SEQ_NO_RANGE) { "seqNo out of range $SEQ_NO_RANGE" }
        require(segmentSpeeds.isNotEmpty()) { "segmentSpeeds must not be empty" }
    }

    override val requestOpcode: Byte = if (isUpdate) UPDATE_REQUEST_OPCODE else SET_REQUEST_OPCODE
    override val expectedResponseOpcode: Byte = if (isUpdate) UPDATE_RESPONSE_OPCODE else SET_RESPONSE_OPCODE

    override fun encode(): ByteArray =
        byteArrayOf(requestOpcode, seqNo.toByte()) +
            segmentSpeeds.flatMap { encodeUnitCenti(it).asList() }.toByteArray()

    override fun decode(responsePayload: ByteArray): SimpleResultResponse {
        requireResponseFrame(responsePayload, expectedResponseOpcode, MIN_RESPONSE_LENGTH)
        return SimpleResultResponse(responsePayload.u(1))
    }

    companion object {

        const val SET_REQUEST_OPCODE: Byte = 0x13
        const val SET_RESPONSE_OPCODE: Byte = 0x73
        const val UPDATE_REQUEST_OPCODE: Byte = 0x21
        const val UPDATE_RESPONSE_OPCODE: Byte = 0x81.toByte()
        private val SEQ_NO_RANGE = 0..2
        private const val MIN_RESPONSE_LENGTH = 2
    }
}
