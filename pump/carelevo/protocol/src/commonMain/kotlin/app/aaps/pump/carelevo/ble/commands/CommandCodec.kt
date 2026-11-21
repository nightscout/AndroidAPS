package app.aaps.pump.carelevo.ble.commands

import app.aaps.pump.carelevo.ble.BleResponse
import kotlin.math.floor

/**
 * Shared wire encode/decode helpers for the CareLevo [app.aaps.pump.carelevo.ble.BleCommand]s.
 */

/** Unsigned byte at [index] as Int. */
internal fun ByteArray.u(index: Int): Int = this[index].toUByte().toInt()

/** A byte as two upper case hex digits, as `"%02X"` wrote it: `0x91` is "91". */
internal fun hex(value: Byte): String = value.toUByte().toString(HEX_RADIX).uppercase().padStart(2, '0')

/**
 * [value] in hundredths, rounded HALF_UP as the number is written: 1.005 becomes 101, although its binary value is
 * 1.00499999... This is what `BigDecimal.valueOf(value).setScale(2, HALF_UP)` gives, without `BigDecimal`, so it runs
 * on every platform.
 *
 * Why it is exact: the half-way point between c and c + 1 hundredths is the decimal (2c + 1) / 200. One division of
 * two exact numbers gives the double nearest to it, `boundary(c)`. A value at or above that double is written at or
 * above the half-way point, so HALF_UP rounds it up; a value below it is written below the half-way point. So the
 * result is the c whose two boundaries hold [value]. `DoseEncodingEquivalenceTest` checks it against `BigDecimal`.
 */
internal fun hundredthsHalfUp(value: Double): Long {
    require(value.isFinite() && value >= 0.0) { "not a dose: $value" }
    var hundredths = floor(value * HUNDRED).toLong()
    // value * 100 may be off by one hundredth; the boundaries settle it.
    while (value >= halfWayAbove(hundredths)) hundredths++
    while (hundredths > 0 && value < halfWayAbove(hundredths - 1)) hundredths--
    return hundredths
}

/** The double nearest to the half-way point between [hundredths] and the next hundredth. */
private fun halfWayAbove(hundredths: Long): Double = (2 * hundredths + 1).toDouble() / (2 * HUNDRED)

/**
 * Encode a unit value as 2 bytes `[intPart, centiPart]`, HALF_UP to 2 dp (see [hundredthsHalfUp]). Range validation
 * stays at the command (min/max belongs to the specific op).
 */
internal fun encodeUnitCenti(value: Double): ByteArray {
    val hundredths = hundredthsHalfUp(value)
    return byteArrayOf((hundredths / HUNDRED_LONG).toByte(), (hundredths % HUNDRED_LONG).toByte())
}

/** Fail fast unless [payload] starts with [opcode] and is at least [minLength] bytes. */
internal fun requireResponseFrame(payload: ByteArray, opcode: Byte, minLength: Int) {
    require(payload.isNotEmpty() && payload[0] == opcode) { wrongOpcodeMessage(opcode, payload) }
    require(payload.size >= minLength) { "response too short: ${payload.size} < $minLength" }
}

/** "expected opcode 0x91, got 0x05", or "got empty". */
internal fun wrongOpcodeMessage(expected: Byte, payload: ByteArray): String =
    "expected opcode 0x${hex(expected)}, got " + (payload.getOrNull(0)?.let { "0x${hex(it)}" } ?: "empty")

/**
 * Response carrying only the pump result code (`data[1]`) — the common 2-byte `cmd,result` reply used by
 * most write acknowledgements (buzz-mode, cancels, basal-program writes, …). `resultCode` 0 = SUCCESS;
 * consumers map it.
 */
data class SimpleResultResponse(val resultCode: Int) : BleResponse

private const val HEX_RADIX = 16
private const val HUNDRED = 100.0
private const val HUNDRED_LONG = 100L
