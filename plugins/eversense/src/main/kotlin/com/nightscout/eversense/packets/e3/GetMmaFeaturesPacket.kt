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
class GetMmaFeaturesPacket : EversenseBasePacket() {
    override fun getRequestData(): ByteArray = EversenseE3Memory.MmaFeatures.getRequestData()
    override fun parseResponse(): Response? {
        if (receivedData.size < getStartIndex() + 1) return null
        return Response(value = receivedData[getStartIndex()].toInt() and 0xFF)
    }
    data class Response(val value: Int) : EversenseBasePacket.Response()
}