package com.nightscout.eversense.packets.e3

import com.nightscout.eversense.enums.EversenseE3Memory
import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversensePacket

@EversensePacket(
    requestId = EversenseE3Packets.ReadSingleByteSerialFlashRegisterCommandId,
    responseId = EversenseE3Packets.ReadSingleByteSerialFlashRegisterResponseId,
    typeId = 0,
    securityType = EversenseSecurityType.None
)
class GetHighGlucoseRepeatIntervalPacket : EversenseBasePacket() {
    override fun getRequestData(): ByteArray = EversenseE3Memory.HighGlucoseAlarmRepeatIntervalDay.getRequestData()
    override fun parseResponse(): Response? {
        if (receivedData.size < getStartIndex() + 1) return null
        return Response(intervalMinutes = receivedData[getStartIndex()].toInt() and 0xFF)
    }
    data class Response(val intervalMinutes: Int) : EversenseBasePacket.Response()
}