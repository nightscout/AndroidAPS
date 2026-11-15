package app.aaps.pump.carelevo.ble.commands

import app.aaps.pump.carelevo.ble.BleResponse
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToInt

/**
 * Shared wire encode/decode helpers for the CareLevo [app.aaps.pump.carelevo.ble.BleCommand]s.
 */

/** Unsigned byte at [index] as Int. */
internal fun ByteArray.u(index: Int): Int = this[index].toUByte().toInt()

/**
 * Encode a unit value as 2 bytes `[intPart, centiPart]`, HALF_UP to 2 dp. Range validation stays at
 * the command (min/max belongs to the specific op).
 */
internal fun encodeUnitCenti(value: Double): ByteArray {
    // valueOf (canonical decimal via Double.toString), NOT BigDecimal(double) (exact binary
    // expansion) — the binary form can sit epsilon-below a .xx5 boundary and flip HALF_UP.
    val rounded = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toDouble()
    val whole = rounded.toInt()
    val centi = ((rounded - whole) * 100).roundToInt()
    return byteArrayOf(whole.toByte(), centi.toByte())
}

/** Fail fast unless [payload] starts with [opcode] and is at least [minLength] bytes. */
internal fun requireResponseFrame(payload: ByteArray, opcode: Byte, minLength: Int) {
    require(payload.isNotEmpty() && payload[0] == opcode) {
        "expected opcode 0x${"%02X".format(opcode)}, got " +
            (payload.getOrNull(0)?.let { "0x${"%02X".format(it)}" } ?: "empty")
    }
    require(payload.size >= minLength) { "response too short: ${payload.size} < $minLength" }
}

/**
 * Response carrying only the pump result code (`data[1]`) — the common 2-byte `cmd,result` reply used by
 * most write acknowledgements (buzz-mode, cancels, basal-program writes, …). `resultCode` 0 = SUCCESS;
 * consumers map it.
 */
data class SimpleResultResponse(val resultCode: Int) : BleResponse
