package com.nightscout.eversense.packets.e3

import com.nightscout.eversense.enums.EversenseAlarm
import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversensePacket
import com.nightscout.eversense.packets.e3.util.EversenseE3Parser
import com.nightscout.eversense.packets.e3.util.EversenseE3Writer

@EversensePacket(
    requestId = EversenseE3Packets.ReadAllSensorGlucoseAlertsInSpecifiedRangeCommandId,
    responseId = EversenseE3Packets.ReadAllSensorGlucoseAlertsInSpecifiedRangeResponseId,
    typeId = 0,
    securityType = EversenseSecurityType.None
)
class GetAlertLogPacket(private val index: Int) : EversenseBasePacket() {

    override fun getRequestData(): ByteArray {
        return EversenseE3Writer.writeInt16(index) + EversenseE3Writer.writeInt16(index)
    }

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null

        val s = getStartIndex()
        val recordIndex = (receivedData[s].toInt() and 0xFF) or ((receivedData[s + 1].toInt() and 0xFF) shl 8)
        val datetime = EversenseE3Parser.readDate(receivedData, s + 2) + EversenseE3Parser.readTime(receivedData, s + 4)
        val alarmCode = receivedData[s + 7].toInt() and 0xFF

        return Response(
            index = recordIndex,
            datetime = datetime,
            alarm = EversenseAlarm.from(alarmCode)
        )
    }

    data class Response(
        val index: Int,
        val datetime: Long,
        val alarm: EversenseAlarm
    ) : EversenseBasePacket.Response()
}
