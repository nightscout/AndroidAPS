package app.aaps.pump.carelevo.ble.commands

import app.aaps.pump.carelevo.ble.BleCommand
import app.aaps.pump.carelevo.ble.BleMultiCommand
import org.joda.time.DateTime

/**
 * Shared `CMD_SET_TIME_REQ` (0x11) request encoding:
 * `[0] 0x11, [1] subId, [2..7] dateTime = [yy,MM,dd,HH,mm,ss], [8..9] volume = [vol/100, vol%100], [10] aidMode]`.
 *
 * [dateTime] is an explicit parameter (caller passes `DateTime.now()`) so `encode()` is deterministic and
 * unit-testable. Year = last two digits (`year % 100`); volume validated 0..300.
 */
internal fun encodeSetTime(subId: Int, volume: Int, aidMode: Int, dateTime: DateTime): ByteArray {
    require(volume in VOLUME_RANGE) { "volume out of range $VOLUME_RANGE" }
    // last two digits via arithmetic — can't throw on unusual years.
    val yy = dateTime.year % 100
    return byteArrayOf(
        SET_TIME_REQUEST_OPCODE,
        subId.toByte(),
        yy.toByte(),
        dateTime.monthOfYear.toByte(),
        dateTime.dayOfMonth.toByte(),
        dateTime.hourOfDay.toByte(),
        dateTime.minuteOfHour.toByte(),
        dateTime.secondOfMinute.toByte(),
        (volume / HUNDRED).toByte(),
        (volume % HUNDRED).toByte(),
        aidMode.toByte()
    )
}

internal const val SET_TIME_REQUEST_OPCODE: Byte = 0x11
private val VOLUME_RANGE = 0..300
private const val HUNDRED = 100

/**
 * `CMD_SET_TIME_REQ` (0x11) → `CMD_SET_TIME_RES` (0x71). Sets the patch clock — the normal path (e.g. the
 * timezone/DST update, subId=1). Response is result-only → [SimpleResultResponse].
 *
 * NOTE the dual shape of 0x11: during **activation** the same write is used with subId=0 to make the patch
 * emit its info reports (0x93+0x94) and 0x71 is ignored — that path is [SetTimeForPatchInfoCommand].
 */
class SetTimeCommand(
    private val subId: Int,
    private val volume: Int,
    private val aidMode: Int,
    private val dateTime: DateTime
) : BleCommand<SimpleResultResponse> {

    override val requestOpcode: Byte = SET_TIME_REQUEST_OPCODE
    override val expectedResponseOpcode: Byte = RESPONSE_OPCODE

    override fun encode(): ByteArray = encodeSetTime(subId, volume, aidMode, dateTime)

    override fun decode(responsePayload: ByteArray): SimpleResultResponse {
        requireResponseFrame(responsePayload, RESPONSE_OPCODE, MIN_RESPONSE_LENGTH)
        return SimpleResultResponse(responsePayload.u(1))
    }

    companion object {

        const val RESPONSE_OPCODE: Byte = 0x71
        private const val MIN_RESPONSE_LENGTH = 2
    }
}

/**
 * `CMD_SET_TIME_REQ` (0x11) → RPT1 (0x93) + RPT2 (0x94). The **activation** variant of set-time (subId=0):
 * the same 0x11 write triggers the patch to emit its two info reports, which this collects as a
 * [BleMultiCommand] and decodes into a [PatchInfoResponse] (reusing [PatchInfoCommand]'s wire decode).
 * The 0x71 set-time ack is ignored on this path — see [SetTimeCommand] for the normal shape.
 */
class SetTimeForPatchInfoCommand(
    private val subId: Int,
    private val volume: Int,
    private val aidMode: Int,
    private val dateTime: DateTime
) : BleMultiCommand<PatchInfoResponse> {

    override val requestOpcode: Byte = SET_TIME_REQUEST_OPCODE
    override val expectedResponseOpcodes: Set<Byte> = setOf(PatchInfoCommand.RPT1_OPCODE, PatchInfoCommand.RPT2_OPCODE)

    override fun encode(): ByteArray = encodeSetTime(subId, volume, aidMode, dateTime)

    override fun decode(responses: Map<Byte, ByteArray>): PatchInfoResponse = PatchInfoCommand().decode(responses)
}
