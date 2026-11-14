package app.aaps.plugins.eversense.protocol

import app.aaps.plugins.eversense.enums.EversenseTrendArrow
import app.aaps.plugins.eversense.packets.e3.util.EversenseE3Parser
import app.aaps.plugins.eversense.util.EversenseLogger

/** Generation-specific layouts share primitives, while preserving their different validity rules. */
internal object GlucoseDecoders {
    data class Reading(val timestamp: Long, val glucose: Int, val trend: EversenseTrendArrow,
                       val signal: Int = 0, val sensorId: String = "", val raw: String)

    fun e3(bytes: UByteArray): Reading? {
        if (bytes.isEmpty()) return null
        val glucose = EversenseE3Parser.readGlucose(bytes, 9)
        if (glucose !in 20..450) return null
        return Reading(
            timestamp = EversenseE3Parser.readDate(bytes, 4) + EversenseE3Parser.readTime(bytes, 6),
            glucose = glucose,
            trend = trend(bytes[13].toInt(), extended = false),
            raw = hex(bytes)
        )
    }

    fun v2(bytes: UByteArray, fallbackSensorLength: Int): Reading? {
        if (bytes.isEmpty()) return null
        val length = bytes[11].toInt().takeUnless { it == 0 } ?: fallbackSensorLength
        val signal = BinaryFields.unsigned(bytes, 22 + length, 2).toInt()
        val sensor = bytes.copyOfRange(12, 12 + length)
        val glucose = BinaryFields.unsigned(bytes, 20 + length, 2).toInt()
        // The frozen 365 implementation rejects 450 itself; E3 accepts that boundary.
        if (glucose >= 450) {
            EversenseLogger.warning("GetGlucoseDataPacket", "Glucose exceeds safety limits: $glucose — rejecting")
            return null
        }
        return Reading(
            timestamp = BinaryFields.sensorTimestamp(bytes.copyOfRange(12 + length, 20 + length)),
            glucose = glucose,
            trend = trend(bytes[164 + length].toInt(), extended = true),
            signal = signal,
            sensorId = hex(sensor),
            raw = hex(bytes)
        )
    }

    fun trend(value: Int, extended: Boolean, unknown: EversenseTrendArrow = EversenseTrendArrow.NONE): EversenseTrendArrow = when (value) {
        1 -> EversenseTrendArrow.SINGLE_DOWN
        2 -> EversenseTrendArrow.FORTY_FIVE_DOWN
        4 -> EversenseTrendArrow.FLAT
        8 -> EversenseTrendArrow.FORTY_FIVE_UP
        16 -> EversenseTrendArrow.SINGLE_UP
        32 -> if (extended) EversenseTrendArrow.SINGLE_DOWN else unknown
        64 -> if (extended) EversenseTrendArrow.SINGLE_UP else unknown
        else -> unknown
    }

    private fun hex(bytes: UByteArray): String {
        val digits = "0123456789abcdef"
        return CharArray(bytes.size * 2).also { chars ->
            bytes.forEachIndexed { index, byte ->
                chars[index * 2] = digits[byte.toInt() ushr 4]
                chars[index * 2 + 1] = digits[byte.toInt() and 15]
            }
        }.concatToString()
    }
}
