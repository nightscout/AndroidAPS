package com.nightscout.eversense.packets.e3

import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.enums.EversenseTrendArrow
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversensePacket
import com.nightscout.eversense.packets.e3.util.EversenseE3Parser

@EversensePacket(
    requestId = EversenseE3Packets.ReadSensorGlucoseCommandId,
    responseId = EversenseE3Packets.ReadSensorGlucoseResponseId,
    typeId = 0,
    securityType = EversenseSecurityType.None
)
class GetCurrentGlucosePacket : EversenseBasePacket() {

    override fun getRequestData(): ByteArray {
        return ByteArray(0)
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) {
            return null
        }

        return Response(
            datetime = EversenseE3Parser.readDate(receivedData, 4) + EversenseE3Parser.readTime(receivedData, 6),
            glucoseInMgDl = EversenseE3Parser.readGlucose(receivedData, 9),
            trend = parseTrend(receivedData[13].toInt()),
        )
    }

    private fun parseTrend(value: Int): EversenseTrendArrow {
        return when(value) {
            1 -> EversenseTrendArrow.SINGLE_DOWN
            2 -> EversenseTrendArrow.FORTY_FIVE_DOWN
            4 -> EversenseTrendArrow.FLAT
            8 -> EversenseTrendArrow.FORTY_FIVE_UP
            16 -> EversenseTrendArrow.SINGLE_UP
            else -> EversenseTrendArrow.NONE
        }
    }

    data class Response(val datetime: Long, val glucoseInMgDl: Int, val trend: EversenseTrendArrow) : EversenseBasePacket.Response()
}