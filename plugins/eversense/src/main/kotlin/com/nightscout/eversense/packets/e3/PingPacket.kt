package com.nightscout.eversense.packets.e3

import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversensePacket

@EversensePacket(
    requestId = EversenseE3Packets.PingCommandId,
    responseId = EversenseE3Packets.PingResponseId,
    typeId = 0,
    securityType = EversenseSecurityType.None
)
class PingPacket : EversenseBasePacket() {

    override fun getRequestData(): ByteArray = ByteArray(0)

    override fun parseResponse(): Response? {
        if (receivedData.isEmpty()) return null
        return Response()
    }

    class Response : EversenseBasePacket.Response()
}
