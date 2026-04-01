package com.nightscout.eversense.packets.e365

import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversensePacket

@EversensePacket(
    requestId = Eversense365Packets.ReadCommandId,
    responseId = Eversense365Packets.ReadResponseId,
    typeId = Eversense365Packets.ReadPing,
    securityType = EversenseSecurityType.SecureV2
)
class Ping365Packet : EversenseBasePacket() {
    override fun getRequestData(): ByteArray = byteArrayOf()
    override fun parseResponse(): Response = Response()
    class Response : EversenseBasePacket.Response()
}