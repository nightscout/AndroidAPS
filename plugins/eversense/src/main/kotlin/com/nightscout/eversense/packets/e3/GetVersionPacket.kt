package com.nightscout.eversense.packets.e3

import com.nightscout.eversense.enums.EversenseE3Memory
import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversensePacket

@EversensePacket(
    requestId = EversenseE3Packets.ReadFourByteSerialFlashRegisterCommandId,
    responseId = EversenseE3Packets.ReadFourByteSerialFlashRegisterResponseId,
    typeId = 0,
    securityType = EversenseSecurityType.None
)
class GetVersionPacket : EversenseBasePacket() {
    override fun getRequestData(): ByteArray = EversenseE3Memory.TransmitterSoftwareVersion.getRequestData()
    override fun parseResponse(): Response? {
        val start = getStartIndex()
        if (receivedData.size < start + 4) return null
        val version = (start until start + 4).map { receivedData[it].toInt().toChar() }.joinToString("")
        return Response(version = version.trim())
    }
    data class Response(val version: String) : EversenseBasePacket.Response()
}