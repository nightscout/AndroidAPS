package app.aaps.plugins.eversense.packets.e3

import app.aaps.plugins.eversense.enums.EversenseE3Memory
import app.aaps.plugins.eversense.enums.EversenseSecurityType
import app.aaps.plugins.eversense.packets.EversenseBasePacket
import app.aaps.plugins.eversense.packets.EversensePacket

@EversensePacket(
    requestId = EversenseE3Packets.ReadTwoByteSerialFlashRegisterCommandId,
    responseId = EversenseE3Packets.ReadTwoByteSerialFlashRegisterResponseId,
    typeId = 0,
    securityType = EversenseSecurityType.None
)
class GetSignalStrengthRawPacket : EversenseBasePacket() {

    companion object {
        // Raw thresholds matching iOS EversenseKit SignalStrength.swift rawThreshold values
        const val THRESHOLD_EXCELLENT = 1600
        const val THRESHOLD_GOOD = 1300
        const val THRESHOLD_LOW = 800
        const val THRESHOLD_VERY_LOW = 500
        const val THRESHOLD_POOR = 350
    }

    override fun getRequestData(): ByteArray = EversenseE3Memory.SensorFieldCurrentRaw.getRequestData()

    override fun parseResponse(): Response? {
        val start = getStartIndex()
        if (receivedData.size < start + 2) return null

        // Little-endian UInt16 — matches iOS: UInt16(data[start]) | (UInt16(data[start + 1]) << 8)
        val raw = (receivedData[start].toInt() and 0xFF) or
                  ((receivedData[start + 1].toInt() and 0xFF) shl 8)

        // Classify the RAW flash value using iOS EversenseKit SignalStrength.threshold
        // values (903/705/494/395/350), then map to a 0-100 percentage that the
        // placement activity bar thresholds (75/48/30/28/25) bucket correctly.
        // The previous code used raw/20, which systematically under-reported signal
        // (a good placement at raw>=903 displayed as "Fair" instead of "Excellent").
        val signalPercent = when {
            raw >= 903 -> 80   // Excellent -> 5 bars
            raw >= 705 -> 55   // Good      -> 4 bars
            raw >= 494 -> 35   // Low       -> 3 bars
            raw >= 395 -> 28   // Very low  -> 2 bars
            raw >= 350 -> 25   // Poor      -> 1 bar
            else       -> 0    // No signal -> 0 bars
        }

        return Response(rawValue = raw, signalStrength = signalPercent)
    }

    data class Response(
        val rawValue: Int,
        val signalStrength: Int  // 0-100 scaled, matching iOS implementation
    ) : EversenseBasePacket.Response()
}
