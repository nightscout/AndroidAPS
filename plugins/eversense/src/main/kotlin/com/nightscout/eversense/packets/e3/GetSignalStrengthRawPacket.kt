package com.nightscout.eversense.packets.e3

import com.nightscout.eversense.enums.EversenseE3Memory
import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversensePacket

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

        // Scale to 0-100 matching iOS PlacementGuideViewModel: rawValue / 20
        val signalPercent = (raw / 20).coerceIn(0, 100)

        return Response(rawValue = raw, signalStrength = signalPercent)
    }

    data class Response(
        val rawValue: Int,
        val signalStrength: Int  // 0-100 scaled, matching iOS implementation
    ) : EversenseBasePacket.Response()
}